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

package org.smslib.smsserver.interfaces;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import org.smslib.InboundMessage;
import org.smslib.OutboundBinaryMessage;
import org.smslib.OutboundMessage;
import org.smslib.StatusReportMessage;
import org.smslib.Message.MessageEncodings;
import org.smslib.Message.MessageTypes;
import org.smslib.OutboundMessage.MessageStatuses;
import org.smslib.smsserver.AInterface;
import org.smslib.smsserver.SMSServer;

/**
 * Interface for database communication with SMSServer. <br />
 * Inbound messages and calls are logged in special tables, outbound messages
 * are retrieved from another table.
 */
public class Database extends AInterface<Integer>
{
	public Database(String myInterfaceId, Properties myProps, SMSServer myServer, InterfaceTypes myType)
	{
		super(myInterfaceId, myProps, myServer, myType);
		setDescription("Default database interface.");
	}

	@Override
	public void start() throws Exception
	{
		Connection con;
		Statement cmd;
		Class.forName(getProperty("driver"));
		con = getDbConnection();
		if (con != null)
		{
			cmd = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			cmd.executeUpdate("update " + getProperty("tables.sms_out", "smsserver_out") + " set status = 'U' where status = 'Q'");
			con.commit();
			cmd.close();
			con.close();
		}
		getService().getLogger().logInfo("SMSServer: Interface Database started.", null, null);
	}

	@Override
	public void stop() throws Exception
	{
		Connection con;
		Statement cmd;
		con = getDbConnection();
		if (con != null)
		{
			cmd = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			cmd.executeUpdate("update " + getProperty("tables.sms_out", "smsserver_out") + " set status = 'U' where status = 'Q'");
			con.commit();
			cmd.close();
			con.close();
		}
		getService().getLogger().logInfo("SMSServer: Interface Database started.", null, null);
	}

	@Override
	public void CallReceived(String gtwId, String callerId) throws Exception
	{
		Connection con;
		Statement cmd;
		ResultSet rs;
		con = getDbConnection();
		cmd = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
		rs = cmd.executeQuery("select * from " + getProperty("tables.calls", "smsserver_calls") + " where id = -1");
		rs.moveToInsertRow();
		rs.updateTimestamp("call_date", new Timestamp(new java.util.Date().getTime()));
		rs.updateString("gateway_id", gtwId);
		rs.updateString("caller_id", callerId);
		rs.insertRow();
		con.commit();
		rs.close();
		cmd.close();
		con.close();
	}

	@Override
	public void MessagesReceived(Collection<InboundMessage> msgList) throws Exception
	{
		Connection con;
		Statement cmd;
		ResultSet rs;
		con = getDbConnection();
		cmd = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
		rs = cmd.executeQuery("select * from " + getProperty("tables.sms_in", "smsserver_in") + " where id = -1");
		for (InboundMessage msg : msgList)
		{
			if ((msg.getType() == MessageTypes.INBOUND) || (msg.getType() == MessageTypes.STATUSREPORT))
			{
				rs.moveToInsertRow();
				rs.updateInt("process", 0);
				switch (msg.getEncoding())
				{
					case ENC7BIT:
						rs.updateString("encoding", "7");
						break;
					case ENC8BIT:
						rs.updateString("encoding", "8");
						break;
					case ENCUCS2:
						rs.updateString("encoding", "U");
						break;
					case ENCCUSTOM:
						rs.updateString("encoding", "C");
						break;
				}
				switch (msg.getType())
				{
					case INBOUND:
						rs.updateString("type", "I");
						rs.updateString("originator", msg.getOriginator());
						if (msg.getDate() != null) rs.updateTimestamp("message_date", new Timestamp(msg.getDate().getTime()));
						rs.updateString("original_ref_no", null);
						rs.updateTimestamp("original_receive_date",null);
						break;
					case STATUSREPORT:
						rs.updateString("type", "S");
						rs.updateString("originator", ((StatusReportMessage) msg).getRecipient());
						if (((StatusReportMessage) msg).getSent() != null) rs.updateTimestamp("message_date", new Timestamp(((StatusReportMessage) msg).getSent().getTime()));
						rs.updateString("original_ref_no", ((StatusReportMessage) msg).getRefNo());
						if (((StatusReportMessage) msg).getReceived() != null) rs.updateTimestamp("original_receive_date", new Timestamp(((StatusReportMessage) msg).getReceived().getTime()));
						if (getProperty("update_outbound_on_statusreport", "no").equalsIgnoreCase("yes"))
						{
							Statement cmd2;
							ResultSet rs2;
							cmd2 = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
							rs2 = cmd2.executeQuery("select status from " + getProperty("tables.sms_out", "smsserver_out") + " where recipient = '" + ((StatusReportMessage) msg).getRecipient() + "' and ref_no = '" + ((StatusReportMessage) msg).getRefNo() + "'");
							if (rs2.next())
							{
								switch (((StatusReportMessage) msg).getStatus())
								{
									case DELIVERED:
										rs2.updateString("status", "D");
										break;
									case KEEPTRYING:
										rs2.updateString("status", "P");
										break;
									case ABORTED:
										rs2.updateString("status", "A");
										break;
									case UNKNOWN:
										break;
								}
								rs2.updateRow();
							}
							rs2.close();
							cmd2.close();
						}
						break;
					default:
						break;
				}
				rs.updateTimestamp("receive_date", new Timestamp(new java.util.Date().getTime()));
				rs.updateString("text", msg.getText().replaceAll("'", "''"));
				rs.updateString("gateway_id", msg.getGatewayId());
				rs.insertRow();
			}
		}
		rs.close();
		cmd.close();
		con.commit();
		con.close();
	}

	@Override
	public Collection<OutboundMessage> getMessagesToSend() throws Exception
	{
		Collection<OutboundMessage> msgList = new ArrayList<OutboundMessage>();
		OutboundMessage msg;
		Connection con;
		Statement cmd;
		ResultSet rs;
		int msgCount;
		msgCount = 0;
		con = getDbConnection();
		if (con != null)
		{
			cmd = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			if (getProperty("type").equalsIgnoreCase("mysql")) rs = cmd.executeQuery("select * from " + getProperty("tables.sms_out", "smsserver_out") + " where status = 'U' order by priority, id");
			else rs = cmd.executeQuery("select * from " + getProperty("tables.sms_out", "smsserver_out") + " where status = 'U' order by priority desc, id");
			while (rs.next())
			{
				if (rs.getString("encoding").charAt(0) == '8') msg = new OutboundBinaryMessage(rs.getString("recipient"), rs.getString("text").getBytes());
				else msg = new OutboundMessage(rs.getString("recipient"), rs.getString("text"));
				msg.setPriority(rs.getInt("priority"));
				if (getServer().checkPriorityTimeFrame(msg.getPriority()))
				{
					switch (rs.getString("encoding").charAt(0))
					{
						case '7':
							msg.setEncoding(MessageEncodings.ENC7BIT);
							break;
						case '8':
							msg.setEncoding(MessageEncodings.ENC8BIT);
							break;
						case 'U':
							msg.setEncoding(MessageEncodings.ENCUCS2);
							break;
					}
					if (rs.getInt("status_report") == 1) msg.setStatusReport(true);
					if (rs.getInt("flash_sms") == 1) msg.setFlashSms(true);
					if (rs.getInt("src_port") != -1)
					{
						msg.setSrcPort(rs.getInt("src_port"));
						msg.setDstPort(rs.getInt("dst_port"));
					}
					if (rs.getString("originator") != null) msg.setFrom(rs.getString("originator"));
					msg.setGatewayId(rs.getString("gateway_id"));
					msgList.add(msg);
					getMessageCache().put(msg.getMessageId(), rs.getInt("id"));
					rs.updateString("status", "Q");
					rs.updateRow();
					con.commit();
					msgCount++;
					if (msgCount > Integer.parseInt(getProperty("batch_size"))) break;
				}
			}
			rs.close();
			cmd.close();
			con.close();
		}
		return msgList;
	}

	@Override
	public void markMessage(OutboundMessage msg) throws Exception
	{
		Connection con = null;
		ResultSet rs = null;
		Statement cmd = null;
		con = getDbConnection();
		cmd = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
		rs = cmd.executeQuery("select * from " + getProperty("tables.sms_out", "smsserver_out") + " where id = " + getMessageCache().get(msg.getMessageId()));
		if (rs.next())
		{
			if (msg.getMessageStatus() == MessageStatuses.SENT)
			{
				rs.updateString("status", "S");
				rs.updateTimestamp("sent_date", new Timestamp(msg.getDispatchDate().getTime()));
				rs.updateString("gateway_id", msg.getGatewayId());
				rs.updateString("ref_no", msg.getRefNo());
			}
			else if (msg.getMessageStatus() == MessageStatuses.FAILED)
			{
				int errors = rs.getInt("errors");
				errors++;
				rs.updateInt("errors", errors);
				if (errors > Integer.parseInt(getProperty("retries", "2"))) rs.updateString("status", "F");
				else rs.updateString("status", "U");
			}
			rs.updateRow();
			con.commit();
			rs.close();
			cmd.close();
			con.close();
		}
		getMessageCache().remove(msg.getMessageId());
	}

	private Connection getDbConnection()
	{
		Connection dbCon = null;
		try
		{
			dbCon = DriverManager.getConnection(getProperty("url"), getProperty("username", ""), getProperty("password", ""));
			dbCon.setAutoCommit(false);
		}
		catch (SQLException e)
		{
			getService().getLogger().logError("SMSServer: Database Interface: error!", e, null);
		}
		return dbCon;
	}
}
