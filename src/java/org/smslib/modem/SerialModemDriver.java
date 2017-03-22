// SMSLib for Java v3
// A Java API library for sending and receiving SMS via a GSM modem
// or other supported gateways.
// Web Site: http://www.smslib.org
//
// Copyright (C) 2002-2008, Thanasis Delenikas, Athens/GREECE.
// SMSLib is distributed under the terms of the Apache License version 2.0
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.smslib.modem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringTokenizer;
import org.smslib.helper.CommPortIdentifier;
import org.smslib.helper.SerialPort;
import org.smslib.helper.SerialPortEvent;
import org.smslib.helper.SerialPortEventListener;
import org.smslib.AGateway;
import org.smslib.GatewayException;

class SerialModemDriver extends AModemDriver implements SerialPortEventListener
{
	private String comPort;

	private int baudRate;

	private CommPortIdentifier portId;

	private SerialPort serialPort;

	private InputStream in;

	private OutputStream out;

	private ThreadReader threadReader;

	SerialModemDriver(ModemGateway myGateway, String deviceParms)
	{
		super(myGateway, deviceParms);
		StringTokenizer tokens = new StringTokenizer(deviceParms, ":");
		this.comPort = tokens.nextToken();
		this.baudRate = Integer.parseInt(tokens.nextToken());
		this.serialPort = null;
	}

	@SuppressWarnings("unused")
	@Override
	void connectPort() throws GatewayException, IOException, InterruptedException
	{
		if (this.gateway.getService().S.SERIAL_NOFLUSH) this.gateway.getService().getLogger().logInfo("Comm port flushing is disabled.", null, getGateway().getGatewayId());
		if (this.gateway.getService().S.SERIAL_POLLING) this.gateway.getService().getLogger().logInfo("Using polled serial port mode.", null, getGateway().getGatewayId());
		try
		{
			this.gateway.getService().getLogger().logInfo("Opening: " + this.comPort + " @" + this.baudRate, null, getGateway().getGatewayId());
			this.portId = CommPortIdentifier.getPortIdentifier(this.comPort);
			this.serialPort = this.portId.open("org.smslib", 1971);
			this.in = this.serialPort.getInputStream();
			this.out = this.serialPort.getOutputStream();
			if (!this.gateway.getService().S.SERIAL_POLLING)
			{
				this.serialPort.notifyOnDataAvailable(true);
				this.serialPort.notifyOnOutputEmpty(true);
			}
			this.serialPort.notifyOnBreakInterrupt(true);
			this.serialPort.notifyOnFramingError(true);
			this.serialPort.notifyOnOverrunError(true);
			this.serialPort.notifyOnParityError(true);
			if (this.gateway.getService().S.SERIAL_RTSCTS_OUT) this.serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT);
			else this.serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN);
			this.serialPort.addEventListener(this);
			this.serialPort.setSerialPortParams(this.baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			this.serialPort.setInputBufferSize(this.gateway.getService().S.SERIAL_BUFFER_SIZE);
			this.serialPort.setOutputBufferSize(this.gateway.getService().S.SERIAL_BUFFER_SIZE);
			this.serialPort.enableReceiveThreshold(1);
			this.serialPort.enableReceiveTimeout(this.gateway.getService().S.SERIAL_TIMEOUT);
			if (this.gateway.getService().S.SERIAL_POLLING) this.threadReader = new ThreadReader(this.gateway);
		}
		catch (Exception e)
		{
			throw new GatewayException("Comm library exception: " + e.getMessage());
		}
	}

	@SuppressWarnings("unused")
	@Override
	void disconnectPort() throws IOException, InterruptedException
	{
		synchronized (this.SYNC_Reader)
		{
			if (this.gateway.getService().S.SERIAL_POLLING)
			{
				if (this.threadReader != null)
				{
					this.threadReader.interrupt();
					this.threadReader.join();
				}
				this.threadReader = null;
			}
			if (this.serialPort != null) this.serialPort.close();
			this.gateway.getService().getLogger().logInfo("Closing: " + this.comPort + " @" + this.baudRate, null, getGateway().getGatewayId());
		}
	}

	@Override
	void clear() throws IOException
	{
		while (portHasData())
			read();
	}

	@Override
	boolean portHasData() throws IOException
	{
		return (this.in.available() > 0);
	}

	public void serialEvent(SerialPortEvent event)
	{
		int eventType = event.getEventType();
		if (eventType == SerialPortEvent.OE) this.gateway.getService().getLogger().logError("Overrun Error!", null, getGateway().getGatewayId());
		else if (eventType == SerialPortEvent.FE) this.gateway.getService().getLogger().logError("Framing Error!", null, getGateway().getGatewayId());
		else if (eventType == SerialPortEvent.PE) this.gateway.getService().getLogger().logError("Parity Error!", null, getGateway().getGatewayId());
		else if (eventType == SerialPortEvent.DATA_AVAILABLE)
		{
			if (!this.gateway.getService().S.SERIAL_POLLING)
			{
				synchronized (this.SYNC_Reader)
				{
					this.dataReceived = true;
					this.SYNC_Reader.notifyAll();
				}
			}
		}
	}

	@Override
	public void write(char c) throws IOException
	{
		this.out.write(c);
		if (!this.gateway.getService().S.SERIAL_NOFLUSH) this.out.flush();
	}

	@Override
	public void write(byte[] s) throws IOException
	{
		this.out.write(s);
		if (!this.gateway.getService().S.SERIAL_NOFLUSH) this.out.flush();
	}

	@Override
	int read() throws IOException
	{
		return this.in.read();
	}

	private class ThreadReader extends Thread
	{
		private AGateway gtw;

		public ThreadReader(AGateway myGtw)
		{
			this.gtw = myGtw;
			start();
		}

		@Override
		public void run()
		{
			this.gtw.getService().getLogger().logDebug("ThreadReader started.", null, getGateway().getGatewayId());
			while (true)
			{
				try
				{
					sleep(SerialModemDriver.this.gateway.getService().S.SERIAL_POLLING_INTERVAL);
					if (portHasData())
					{
						synchronized (SerialModemDriver.this.SYNC_Reader)
						{
							SerialModemDriver.this.dataReceived = true;
							SerialModemDriver.this.SYNC_Reader.notifyAll();
						}
					}
				}
				catch (InterruptedException e)
				{
					break;
				}
				catch (Exception e)
				{
					this.gtw.getService().getLogger().logError("ThreadReader error. ", e, getGateway().getGatewayId());
				}
			}
			this.gtw.getService().getLogger().logDebug("ThreadReader stopped.", null, getGateway().getGatewayId());
		}
	}
}
