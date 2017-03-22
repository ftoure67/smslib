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
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.smslib.GatewayException;
import org.smslib.InboundMessage;
import org.smslib.TimeoutException;
import org.smslib.AGateway.AsyncEvents;
import org.smslib.AGateway.GatewayStatuses;
import org.smslib.AGateway.Protocols;
import org.smslib.InboundMessage.MessageClasses;
import org.smslib.Message.MessageTypes;

/**
 * Abstract implementation of a generic GSM modem driver.
 */
public abstract class AModemDriver
{
	private static final String rxErrorWithCode = "\\s*[\\p{ASCII}]*\\s*\\+(CM[ES])\\s+ERROR: (\\d+)\\s";

	private static final String rxPlainError = "\\s*[\\p{ASCII}]*\\s*(ERROR|NO CARRIER|NO DIALTONE)\\s";

	protected Object SYNC_Reader, SYNC_Commander;

	protected ModemGateway gateway;

	protected boolean dataReceived;

	volatile boolean connected;

	CharQueue queue;

	private ModemReader modemReader;

	KeepAlive keepAlive;

	AsyncNotifier asyncNotifier;

	AsyncMessageProcessor asyncMessageProcessor;

	/**
	 * Code of last error
	 * 
	 * <pre>
	 *   -1 = empty or invalid response
	 *    0 = OK
	 * 5xxx = CME error xxx
	 * 6xxx = CMS error xxx
	 * 9000 = ERROR
	 * </pre>
	 */
	private int lastError;

	static int OK = 0;

	AModemDriver(ModemGateway myGateway, String deviceParms)
	{
		this.SYNC_Reader = new Object();
		this.SYNC_Commander = new Object();
		this.gateway = myGateway;
		this.connected = false;
		this.dataReceived = false;
		this.queue = new CharQueue();
	}

	abstract void connectPort() throws GatewayException, IOException, InterruptedException;

	abstract void disconnectPort() throws IOException, InterruptedException;

	abstract void clear() throws IOException;

	void connect() throws TimeoutException, GatewayException, IOException, InterruptedException
	{
		String response;
		synchronized (this.SYNC_Commander)
		{
			try
			{
				connectPort();
				this.connected = true;
				this.keepAlive = new KeepAlive();
				this.modemReader = new ModemReader();
				this.asyncNotifier = new AsyncNotifier();
				this.asyncMessageProcessor = new AsyncMessageProcessor();
				clearBuffer();
				getGateway().getATHandler().reset();
				getGateway().getATHandler().sync();
				getGateway().getATHandler().echoOff();
				while (true)
				{
					response = getGateway().getATHandler().getSimStatus();
					while (response.indexOf("BUSY") >= 0)
					{
						getGateway().getService().getLogger().logDebug("SIM found busy, waiting...", null, getGateway().getGatewayId());
						Thread.sleep(getGateway().getService().S.AT_WAIT_SIMPIN);
						response = getGateway().getATHandler().getSimStatus();
					}
					if (response.indexOf("SIM PIN2") >= 0)
					{
						getGateway().getService().getLogger().logDebug("SIM requesting PIN2.", null, getGateway().getGatewayId());
						if ((getGateway().getSimPin2() == null) || (getGateway().getSimPin2().length() == 0)) throw new GatewayException("The GSM modem requires SIM PIN2 to operate.");
						if (!getGateway().getATHandler().enterPin(getGateway().getSimPin2())) throw new GatewayException("SIM PIN2 provided is not accepted by the GSM modem.");
						Thread.sleep(getGateway().getService().S.AT_WAIT_SIMPIN);
						continue;
					}
					else if (response.indexOf("SIM PIN") >= 0)
					{
						getGateway().getService().getLogger().logDebug("SIM requesting PIN.", null, getGateway().getGatewayId());
						if ((getGateway().getSimPin() == null) || (getGateway().getSimPin().length() == 0)) throw new GatewayException("The GSM modem requires SIM PIN to operate.");
						if (!getGateway().getATHandler().enterPin(getGateway().getSimPin())) throw new GatewayException("SIM PIN provided is not accepted by the GSM modem.");
						Thread.sleep(getGateway().getService().S.AT_WAIT_SIMPIN);
						continue;
					}
					else if (response.indexOf("READY") >= 0) break;
					else if (response.indexOf("OK") >= 0) break;
					getGateway().getService().getLogger().logWarn("Cannot understand SIMPIN response: " + response + ", will wait for a while...", null, getGateway().getGatewayId());
					Thread.sleep(getGateway().getService().S.AT_WAIT_SIMPIN);
				}
				getGateway().getATHandler().echoOff();
				getGateway().getATHandler().init();
				getGateway().getATHandler().echoOff();
				waitForNetworkRegistration();
				getGateway().getATHandler().setVerboseErrors();
				if (getGateway().getATHandler().getStorageLocations().length() == 0)
				{
					try
					{
						getGateway().getATHandler().readStorageLocations();
						getGateway().getService().getLogger().logInfo("MEM: Storage Locations Found: " + getGateway().getATHandler().getStorageLocations(), null, getGateway().getGatewayId());
					}
					catch (Exception e)
					{
						getGateway().getATHandler().setStorageLocations("--");
						getGateway().getService().getLogger().logWarn("Storage locations could *not* be retrieved, will proceed with defaults.", e, getGateway().getGatewayId());
					}
				}
				if (!getGateway().getATHandler().setIndications()) getGateway().getService().getLogger().logWarn("Callback indications were *not* set succesfully!", null, getGateway().getGatewayId());
				if (getGateway().getProtocol() == Protocols.PDU)
				{
					if (!getGateway().getATHandler().setPduProtocol()) throw new GatewayException("The GSM modem does not support the PDU protocol.");
				}
				else if (getGateway().getProtocol() == Protocols.TEXT)
				{
					if (!getGateway().getATHandler().setTextProtocol()) throw new GatewayException("The GSM modem does not support the TEXT protocol.");
				}
			}
			catch (TimeoutException t)
			{
				// this is to prevent serial port from being held
				// when a timeout during initialization occurs
				try
				{
					disconnect();
				}
				catch (Exception e)
				{
					// Discard all here.
				}
				throw t;
			}
		}
	}

	void disconnect() throws IOException, InterruptedException
	{
		//deadlocking is still possible in this method
		//however it is nearly impossible, since keepAlive will
		//give other threads opportunity to either exit global "while"
		//or enter waiting state
		
		if (this.keepAlive != null)
		{
			getGateway().getService().getLogger().logDebug("Trying to shutdown keepAlive thread...", null, getGateway().getGatewayId());
		}
		if (this.asyncNotifier != null)
		{
			getGateway().getService().getLogger().logDebug("Trying to shutdown asyncNotifier thread...", null, getGateway().getGatewayId());
		}
		if (this.asyncMessageProcessor != null)
		{
			getGateway().getService().getLogger().logDebug("Trying to shutdown asyncMessageProcessor thread...", null, getGateway().getGatewayId());
		}
		if (this.modemReader != null)
		{
			getGateway().getService().getLogger().logDebug("Trying to shutdown modemReader thread...", null, getGateway().getGatewayId());
		}
			
		this.connected = false;
			
		if (this.keepAlive != null)
			{
			this.keepAlive.interrupt();
			this.keepAlive.join();
			this.keepAlive = null;
		}
		if (this.asyncNotifier != null)
		{
			this.asyncNotifier.interrupt();
			this.asyncNotifier.join();
			this.asyncNotifier = null;
		}
		if (this.asyncMessageProcessor != null)
		{
			this.asyncMessageProcessor.interrupt();
			this.asyncMessageProcessor.join();
			this.asyncMessageProcessor = null;
		}
		if (this.modemReader != null)
		{
			this.modemReader.interrupt();
			this.modemReader.join();
			this.modemReader = null;
		}
		disconnectPort();
	}

	public abstract void write(char c) throws IOException;

	public abstract void write(byte[] s) throws IOException;

	abstract int read() throws IOException;

	abstract boolean portHasData() throws IOException;

	public boolean dataAvailable() throws InterruptedException
	{
		return (this.queue.peek() == -1 ? false : true);
	}

	public void write(String s) throws IOException
	{
		getGateway().getService().getLogger().logDebug("SEND :" + formatLog(new StringBuffer(s)), null, getGateway().getGatewayId());
		write(s.getBytes());
	}

	public void AddToQueue(String s)
	{
		for (int i = 0; i < s.length(); i++)
			this.queue.put((byte) s.charAt(i));
	}

	public String getResponse() throws GatewayException, TimeoutException, IOException
	{
		StringBuffer buffer;
		String response;
		byte c;
		boolean terminate;
		int i;
		String terminators[];
		setLastError(-1);
		terminators = getGateway().getATHandler().getTerminators();
		buffer = new StringBuffer(getGateway().getService().S.SERIAL_BUFFER_SIZE);
		try
		{
			while (true)
			{
				while ((this.queue.peek() == 0x0a) || (this.queue.peek() == 0x0d))
					this.queue.get();
				while (true)
				{
					c = this.queue.get();
					if (getGateway().getService().S.DEBUG_QUEUE) getGateway().getService().getLogger().logDebug("OUT READER QUEUE : " + (int) c + " / " + (char) c, null, getGateway().getGatewayId());
					if (c != 0x0a) buffer.append((char) c);
					else break;
				}
				if (buffer.charAt(buffer.length() - 1) != 0x0d) buffer.append((char) 0x0d);
				response = buffer.toString();
				terminate = false;
				for (i = 0; i < terminators.length; i++)
					if (response.matches(terminators[i]))
					{
						terminate = true;
						break;
					}
				if (terminate) break;
			}
			getGateway().getService().getLogger().logDebug("BUFFER: " + buffer, null, getGateway().getGatewayId());
			if (i >= terminators.length - 4)
			{
				AsyncEvents event = getGateway().getATHandler().processUnsolicitedEvents(buffer.toString());
				if ((event == AsyncEvents.INBOUNDMESSAGE) || (event == AsyncEvents.INBOUNDSTATUSREPORTMESSAGE) || (event == AsyncEvents.INBOUNDCALL)) this.asyncNotifier.setEvent(event, buffer.toString());
				return getResponse();
			}
			// Try to interpret error code
			if (response.matches(rxErrorWithCode))
			{
				Pattern p = Pattern.compile(rxErrorWithCode);
				Matcher m = p.matcher(response);
				if (m.find())
				{
					try
					{
						if (m.group(1).equals("CME"))
						{
							int code = Integer.parseInt(m.group(2));
							setLastError(5000 + code);
						}
						else if (m.group(1).equals("CMS"))
						{
							int code = Integer.parseInt(m.group(2));
							setLastError(6000 + code);
						}
						else throw new GatewayException("Invalid error response: " + m.group(1));
					}
					catch (NumberFormatException e)
					{
						getGateway().getService().getLogger().logDebug("Error on number conversion while interpreting response: ", null, getGateway().getGatewayId());
						throw new GatewayException("Cannot convert error code number.");
					}
				}
				else throw new GatewayException("Cannot match error code. Should never happen!");
			}
			else if (response.matches(rxPlainError)) setLastError(9000);
			else if (response.indexOf("OK") >= 0) setLastError(0);
			else setLastError(10000);
			getGateway().getService().getLogger().logDebug("RECV :" + formatLog(buffer), null, getGateway().getGatewayId());
		}
		catch (InterruptedException e)
		{
			getGateway().getService().getLogger().logWarn("GetResponse() Interrupted.", e, getGateway().getGatewayId());
		}
		catch (TimeoutException e)
		{
			getGateway().getService().getLogger().logDebug("Buffer contents on timeout: " + buffer, null, getGateway().getGatewayId());
			throw e;
		}
		return buffer.toString();
	}

	public void clearBuffer() throws IOException, InterruptedException
	{
		synchronized (this.SYNC_Commander)
		{
			getGateway().getService().getLogger().logDebug("clearBuffer() called.", null, getGateway().getGatewayId());
			Thread.sleep(getGateway().getService().S.SERIAL_CLEAR_WAIT);
			clear();
			this.queue.clear();
		}
	}

	boolean waitForNetworkRegistration() throws GatewayException, TimeoutException, IOException, InterruptedException
	{
		StringTokenizer tokens;
		String response;
		int answer;
		while (true)
		{
			response = getGateway().getATHandler().getNetworkRegistration();
			if (response.indexOf("ERROR") > 0) return false;
			response = response.replaceAll("\\s+OK\\s+", "");
			response = response.replaceAll("\\s+", "");
			response = response.replaceAll("\\+CREG:", "");
			tokens = new StringTokenizer(response, ",");
			tokens.nextToken();
			try
			{
				answer = Integer.parseInt(tokens.nextToken());
			}
			catch (Exception e)
			{
				answer = -1;
			}
			switch (answer)
			{
				case 0:
					getGateway().getService().getLogger().logError("GSM: Auto-registration disabled!", null, getGateway().getGatewayId());
					throw new GatewayException("GSM Network Auto-Registration disabled!");
				case 1:
					getGateway().getService().getLogger().logInfo("GSM: Registered to home network.", null, getGateway().getGatewayId());
					return true;
				case 2:
					getGateway().getService().getLogger().logWarn("GSM: Not registered, searching for network...", null, getGateway().getGatewayId());
					break;
				case 3:
					getGateway().getService().getLogger().logError("GSM: Network registration denied!", null, getGateway().getGatewayId());
					throw new GatewayException("GSM Network Registration denied!");
				case 4:
					getGateway().getService().getLogger().logError("GSM: Unknown registration error!", null, getGateway().getGatewayId());
					throw new GatewayException("GSM Network Registration error!");
				case 5:
					getGateway().getService().getLogger().logInfo("GSM: Registered to foreign network (roaming).", null, getGateway().getGatewayId());
					return true;
				case -1:
					getGateway().getService().getLogger().logInfo("GSM: Invalid CREG response.", null, getGateway().getGatewayId());
					throw new GatewayException("GSM: Invalid CREG response.");
			}
			Thread.sleep(getGateway().getService().S.AT_WAIT_NETWORK);
		}
	}

	private String formatLog(StringBuffer s)
	{
		StringBuffer response = new StringBuffer();
		int i;
		char c;
		for (i = 0; i < s.length(); i++)
		{
			c = s.charAt(i);
			switch (c)
			{
				case 13:
					response.append("(cr)");
					break;
				case 10:
					response.append("(lf)");
					break;
				case 9:
					response.append("(tab)");
					break;
				default:
					if ((c >= 32) && (c < 128))
					{
						response.append(c);
					}
					else
					{
						response.append("(" + (int) c + ")");
					}
					break;
			}
		}
		return response.toString();
	}

	private class CharQueue
	{
		byte[] buffer;

		int bufferStart, bufferEnd;

		public CharQueue()
		{
			this.buffer = null;
			this.bufferStart = 0;
			this.bufferEnd = 0;
		}

		public synchronized void put(byte c)
		{
			if (this.buffer == null) this.buffer = new byte[getGateway().getService().S.SERIAL_BUFFER_SIZE];
			this.buffer[this.bufferEnd] = c;
			this.bufferEnd++;
			if (this.bufferEnd == getGateway().getService().S.SERIAL_BUFFER_SIZE) this.bufferEnd = 0;
			if (getGateway().getService().S.DEBUG_QUEUE) getGateway().getService().getLogger().logDebug("IN READER QUEUE : " + (int) c + " / " + (char) c, null, getGateway().getGatewayId());
			notifyAll();
		}

		public synchronized void put(String s)
		{
			for (int i = 0; i < s.length(); i++)
				put((byte) s.charAt(i));
		}

		public synchronized byte get() throws TimeoutException, InterruptedException
		{
			byte c;
			if (this.buffer == null) this.buffer = new byte[getGateway().getService().S.SERIAL_BUFFER_SIZE];
			while (true)
			{
				try
				{
					if (this.bufferStart == this.bufferEnd) wait(getGateway().getService().S.SERIAL_TIMEOUT);
					if (this.bufferStart == this.bufferEnd) throw new TimeoutException("No response from device.");
					c = this.buffer[this.bufferStart];
					this.bufferStart++;
					if (this.bufferStart == getGateway().getService().S.SERIAL_BUFFER_SIZE) this.bufferStart = 0;
					return c;
				}
				catch (InterruptedException e)
				{
					if (getGateway().getGatewayStatus() == GatewayStatuses.RUNNING) getGateway().getService().getLogger().logWarn("Ignoring InterruptedException in Queue.get().", null, getGateway().getGatewayId());
					else
					{
						getGateway().getService().getLogger().logWarn("Re-throwing InterruptedException in Queue.get() - should be during shutdown...", null, getGateway().getGatewayId());
						throw new InterruptedException();
					}
				}
			}
		}

		public synchronized byte peek() throws InterruptedException
		{
			if (this.buffer == null) this.buffer = new byte[getGateway().getService().S.SERIAL_BUFFER_SIZE];
			while (true)
			{
				try
				{
					if (this.bufferStart == this.bufferEnd) wait(getGateway().getService().S.SERIAL_TIMEOUT);
					if (this.bufferStart == this.bufferEnd) return -1;
					return this.buffer[this.bufferStart];
				}
				catch (InterruptedException e)
				{
					if (getGateway().getGatewayStatus() == GatewayStatuses.RUNNING) getGateway().getService().getLogger().logWarn("Ignoring InterruptedException in Queue.peek().", e, getGateway().getGatewayId());
					else
					{
						getGateway().getService().getLogger().logWarn("Re-throwing InterruptedException in Queue.peek() - should be during shutdown...", e, getGateway().getGatewayId());
						throw new InterruptedException();
					}
				}
			}
		}

		public synchronized String peek(int sizeToRead)
		{
			int i, size;
			StringBuffer result;
			if (this.buffer == null) this.buffer = new byte[getGateway().getService().S.SERIAL_BUFFER_SIZE];
			size = sizeToRead;
			if (this.bufferStart == this.bufferEnd) return "";
			result = new StringBuffer(size);
			i = this.bufferStart;
			while (size > 0)
			{
				if ((this.buffer[i] != 0x0a) && (this.buffer[i] != 0x0d))
				{
					result.append((char) this.buffer[i]);
					size--;
				}
				i++;
				if (i == getGateway().getService().S.SERIAL_BUFFER_SIZE) i = 0;
				if (i == this.bufferEnd) break;
			}
			return result.toString();
		}

		public synchronized void clear()
		{
			this.bufferStart = 0;
			this.bufferEnd = 0;
		}

		public void dump()
		{
			int i;
			i = this.bufferStart;
			while (i < this.bufferEnd)
			{
				System.out.println(this.buffer[i] + " -> " + (char) this.buffer[i]);
				i++;
			}
		}
	}

	private class ModemReader extends Thread
	{
		public ModemReader()
		{
			start();
			getGateway().getService().getLogger().logDebug("ModemReader thread started.", null, getGateway().getGatewayId());
		}

		@Override
		public void run()
		{
			int c;
			String data;
			while (AModemDriver.this.connected)
			{
				try
				{
					synchronized (AModemDriver.this.SYNC_Reader)
					{
						if (!AModemDriver.this.dataReceived) AModemDriver.this.SYNC_Reader.wait();
						if (!AModemDriver.this.connected) break;
						c = read();
						while (c != -1)
						{
							AModemDriver.this.queue.put((byte) c);
							if (!portHasData()) break;
							c = read();
						}
						AModemDriver.this.dataReceived = false;
					}
					data = AModemDriver.this.queue.peek(6);
					for (int i = 0; i < getGateway().getATHandler().getUnsolicitedResponses().length; i++)
						if (data.indexOf(getGateway().getATHandler().getUnsolicitedResponse(i)) >= 0)
						{
							AModemDriver.this.keepAlive.interrupt();
							break;
						}
				}
				catch (InterruptedException e)
				{
					if (!AModemDriver.this.connected) break;
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			getGateway().getService().getLogger().logDebug("ModemReader thread ended.", null, getGateway().getGatewayId());
		}
	}

	private class KeepAlive extends Thread
	{
		public KeepAlive()
		{
			setPriority(MIN_PRIORITY);
			start();
			getGateway().getService().getLogger().logDebug("ModemDriver: KeepAlive thread started.", null, getGateway().getGatewayId());
		}

		@Override
		public void run()
		{
			while (true)
			{
				try
				{
					try
					{
						sleep(getGateway().getService().S.SERIAL_KEEPALIVE_INTERVAL);
					}
					catch (InterruptedException e)
					{
						// Swallow this...
					}
					if (!AModemDriver.this.connected) break;
					if (getGateway().getGatewayStatus() == GatewayStatuses.RUNNING)
					{
						synchronized (AModemDriver.this.SYNC_Commander)
						{
							if (!AModemDriver.this.connected) break;
							getGateway().getService().getLogger().logDebug("** KeepAlive START **", null, getGateway().getGatewayId());
							try
							{
								if (!getGateway().getATHandler().isAlive()) getGateway().setGatewayStatus(GatewayStatuses.RESTART);
							}
							catch (Exception e)
							{
								getGateway().setGatewayStatus(GatewayStatuses.RESTART);
							}
							getGateway().getService().getLogger().logDebug("** KeepAlive END **", null, getGateway().getGatewayId());
						}
					}
				}
				catch (Exception e)
				{
					getGateway().getService().getLogger().logError("ModemDriver: KeepAlive Error.", e, getGateway().getGatewayId());
					getGateway().setGatewayStatus(GatewayStatuses.RESTART);
				}
			}
			getGateway().getService().getLogger().logDebug("ModemDriver: KeepAlive thread ended.", null, getGateway().getGatewayId());
		}
	}

	private class AsyncNotifier extends Thread
	{
		class Event
		{
			AsyncEvents event;

			String response;

			public Event(AsyncEvents myEvent, String myResponse)
			{
				this.event = myEvent;
				this.response = myResponse;
			}

			@Override
			public String toString()
			{
				return "Event: " + this.event + " / Response: " + this.response;
			}
		}

		private BlockingQueue<Event> eventQueue;

		private Object SYNC;

		public AsyncNotifier()
		{
			this.SYNC = new Object();
			this.eventQueue = new LinkedBlockingQueue<Event>();
			setPriority(MIN_PRIORITY);
			start();
			getGateway().getService().getLogger().logDebug("AsyncNotifier thread started.", null, getGateway().getGatewayId());
		}

		protected void setEvent(AsyncEvents event, String response)
		{
			synchronized (this.SYNC)
			{
				Event ev = new Event(event, response);
				getGateway().getService().getLogger().logDebug("Storing AsyncEvent: " + ev, null, getGateway().getGatewayId());
				this.eventQueue.add(ev);
				this.SYNC.notify();
			}
		}

		protected String getMemLoc(String indication)
		{
			Pattern p = Pattern.compile("\\+?\"\\S+\"");
			Matcher m = p.matcher(indication);
			if (m.find()) return indication.substring(m.start(), m.end()).replaceAll("\"", "");
			return "";
		}

		protected int getMemIndex(String indication)
		{
			Pattern p = Pattern.compile("\\+?\\d+");
			Matcher m = p.matcher(indication);
			if (m.find()) return Integer.parseInt(indication.substring(m.start(), m.end()).replaceAll("\"", ""));
			return -1;
		}

		protected String getOriginator(String indication)
		{
			Pattern p = Pattern.compile("\\+?\"\\S+\"");
			Matcher m = p.matcher(indication);
			if (m.find()) return indication.substring(m.start(), m.end()).replaceAll("\"", "");
			return "";
		}

		@SuppressWarnings("deprecation")
		@Override
		public void run()
		{
			String response;
			Event event;
			while (AModemDriver.this.connected)
			{
				try
				{
					event = this.eventQueue.take();
					getGateway().getService().getLogger().logDebug("Processing AsyncEvent: " + event, null, getGateway().getGatewayId());
					if (event.event == AsyncEvents.INBOUNDMESSAGE)
					{
						getGateway().getService().getLogger().logDebug("Inbound message detected!", null, getGateway().getGatewayId());
						event.event = AsyncEvents.NOTHING;
						response = event.response;
						AModemDriver.this.asyncMessageProcessor.setProcess();
					}
					else if (event.event == AsyncEvents.INBOUNDSTATUSREPORTMESSAGE)
					{
						getGateway().getService().getLogger().logDebug("Inbound status report message detected!", null, getGateway().getGatewayId());
						event.event = AsyncEvents.NOTHING;
						response = event.response;
						AModemDriver.this.asyncMessageProcessor.setProcess();
					}
					else if (event.event == AsyncEvents.INBOUNDCALL)
					{
						getGateway().getService().getLogger().logDebug("Inbound call detected!", null, getGateway().getGatewayId());
						event.event = AsyncEvents.NOTHING;
						synchronized (AModemDriver.this.SYNC_Commander)
						{
							getGateway().getATHandler().switchToCmdMode();
							getGateway().getModemDriver().write("ATH\r");
							getGateway().getModemDriver().getResponse();
							response = event.response;
						}
						if (getGateway().getCallNotification() != null) getGateway().getCallNotification().process(getGateway().getGatewayId(), getOriginator(response));
						if (getGateway().getService().getCallNotification() != null) getGateway().getService().getCallNotification().process(getGateway().getGatewayId(), getOriginator(response));
					}
				}
				catch (InterruptedException e)
				{
					if (!AModemDriver.this.connected) break;
				}
				catch (GatewayException e)
				{
					// Swallow this...
				}
				catch (IOException e)
				{
					// Swallow this...
				}
				catch (TimeoutException e)
				{
					// Swallow this...
				}
			}
			AModemDriver.this.gateway.getService().getLogger().logDebug("AsyncNotifier thread ended.", null, getGateway().getGatewayId());
		}
	}

	private class AsyncMessageProcessor extends Thread
	{
		private List<InboundMessage> msgList;

		private Object SYNC;

		private boolean process;

		public AsyncMessageProcessor()
		{
			this.msgList = new ArrayList<InboundMessage>();
			this.SYNC = new Object();
			this.process = false;
			setPriority(MAX_PRIORITY);
			start();
			getGateway().getService().getLogger().logDebug("AsyncMessageProcessor thread started.", null, getGateway().getGatewayId());
		}

		public void setProcess()
		{
			synchronized (this.SYNC)
			{
				if (this.process) return;
				this.process = true;
				this.SYNC.notify();
			}
		}

		@SuppressWarnings("deprecation")
		@Override
		public void run()
		{
			while (AModemDriver.this.connected)
			{
				try
				{
					synchronized (this.SYNC)
					{
						if (!this.process)
						{
							this.SYNC.wait();
							if (!AModemDriver.this.connected) break;
						}
					}
					if ((getGateway().getInboundNotification() != null) || (getGateway().getService().getInboundNotification() != null))
					{
						getGateway().readMessages(this.msgList, MessageClasses.ALL);
						for (InboundMessage msg : this.msgList)
						{
							switch (msg.getType())
							{
								case INBOUND:
									if (getGateway().getInboundNotification() != null) getGateway().getInboundNotification().process(getGateway().getGatewayId(), MessageTypes.INBOUND, msg);
									if (getGateway().getService().getInboundNotification() != null) getGateway().getService().getInboundNotification().process(getGateway().getGatewayId(), MessageTypes.INBOUND, msg);
									break;
								case STATUSREPORT:
									if (getGateway().getInboundNotification() != null) getGateway().getInboundNotification().process(getGateway().getGatewayId(), MessageTypes.STATUSREPORT, msg);
									if (getGateway().getService().getInboundNotification() != null) getGateway().getService().getInboundNotification().process(getGateway().getGatewayId(), MessageTypes.STATUSREPORT, msg);
									break;
								default:
									break;
							}
						}
					}
					this.msgList.clear();
					this.process = false;
				}
				catch (InterruptedException e)
				{
					if (!AModemDriver.this.connected) break;
				}
				catch (GatewayException e)
				{
					// Swallow this...
				}
				catch (IOException e)
				{
					// Swallow this...
				}
				catch (TimeoutException e)
				{
					// Swallow this...
				}
			}
			getGateway().getService().getLogger().logDebug("AsyncMessageProcessor thread ended.", null, getGateway().getGatewayId());
		}
	}

	void setLastError(int myLastError)
	{
		this.lastError = myLastError;
	}

	public int getLastError()
	{
		return this.lastError;
	}

	public String getLastErrorText()
	{
		if (getLastError() == 0) return "OK";
		else if (getLastError() == -1) return "Invalid or empty response";
		else if ((getLastError() / 1000) == 5) return "CME Error " + (getLastError() % 1000);
		else if ((getLastError() / 1000) == 6) return "CMS Error " + (getLastError() % 1000);
		else return "Error: unknown";
	}

	public boolean isOk()
	{
		return (getLastError() == OK);
	}

	ModemGateway getGateway()
	{
		return this.gateway;
	}
}
