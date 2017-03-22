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

package org.smslib.smsserver;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.smslib.InboundMessage;
import org.smslib.OutboundMessage;
import org.smslib.Service;

/**
 * The AInterface abstract class is the base class of all implemented SMSServer
 * interfaces.
 * <p>
 * An SMSServer interface can be thought of as a message producer or a message
 * consumer.
 * <p>
 * SMSServer comes with a couple of ready-made interfaces. If you wish to extend
 * SMSServer with new interface functionality, create your own interface by
 * implementing the current abstract class.
 */
public abstract class AInterface<T>
{
	/**
	 * Class representing SMSServer interface types.
	 */
	public enum InterfaceTypes
	{
		/**
		 * Representing an inbound-only interface.
		 */
		INBOUND,
		/**
		 * Representing an outbound-only interface.
		 */
		OUTBOUND,
		/**
		 * Representing a dual (inbound + outbound) interface.
		 */
		INOUTBOUND
	}

	private String infId;

	private Properties props;

	private SMSServer server;

	private InterfaceTypes type;

	private String description;

	/**
	 * Store to save messageId with interface-specific message identifications
	 * like primary keys or filenames
	 */
	private Map<Long, T> messageIdCache;

	public AInterface(String myInfId, Properties myProps, SMSServer myServer, InterfaceTypes myType)
	{
		this.infId = myInfId;
		this.props = myProps;
		this.server = myServer;
		this.type = myType;
		this.messageIdCache = new HashMap<Long, T>();
	}

	public final Service getService()
	{
		return getServer().getService();
	}

	public final SMSServer getServer()
	{
		return this.server;
	}

	/**
	 * This method is called by SMSServer every time an inbound call is
	 * received. SMSServer calls this method for all available/active
	 * interfaces.
	 * 
	 * @param gtwId
	 *            The Id of the gateway which received the call.
	 * @param callerId
	 *            The caller id.
	 */
	public abstract void CallReceived(String gtwId, String callerId) throws Exception;

	/**
	 * Returns the interface description.
	 * 
	 * @return The interface description.
	 */
	public final String getDescription()
	{
		return this.description;
	}

	/**
	 * Sets the interface description.
	 * 
	 * @param myDescription The interface description.
	 * 
	 */
	public final void setDescription(String myDescription)
	{
		this.description = myDescription;
	}

	public final Map<Long, T> getMessageCache()
	{
		return this.messageIdCache;
	}

	/**
	 * SMSServer calls this method in order to query the interface for messages
	 * that need to be send out.
	 * 
	 * @return A list of Outbound messages to be sent. Return an empty list if
	 *         the interface has no messages for dispatch.
	 * @throws Exception
	 */
	public abstract Collection<OutboundMessage> getMessagesToSend() throws Exception;

	/**
	 * Reads the property key of this interface.
	 * 
	 * @param key
	 *            The key of the property to read.
	 * @return The value of the property or null if not set
	 */
	public final String getProperty(String key)
	{
		return getProperty(key, null);
	}

	/**
	 * Reads the property key of this interface. <br />
	 * The defaultValue is returned if the key is not defined in the properties.
	 * 
	 * @param key
	 *            The key of the property to read.
	 * @param defaultValue
	 *            The defaultValue if key is not defined.
	 * @return The value of the property or defaultValue if not set.
	 */
	public final String getProperty(String key, String defaultValue)
	{
		String value = this.props.getProperty(this.infId + "." + key, defaultValue);
		return value;
	}

	/**
	 * Returns the interface type.
	 * 
	 * @return The interface type.
	 * @see InterfaceTypes
	 */
	public final InterfaceTypes getType()
	{
		return this.type;
	}

	/**
	 * Returns true if the interface is for inbound messaging.
	 * 
	 * @return True if the interface is for inbound messaging.
	 */
	public final boolean isInbound()
	{
		if (InterfaceTypes.INBOUND == this.type || InterfaceTypes.INOUTBOUND == this.type) return true;
		return false;
	}

	/**
	 * Returns true if the interface is for outbound messaging.
	 * 
	 * @return True if the interface is for outbound messaging.
	 */
	public final boolean isOutbound()
	{
		if (InterfaceTypes.OUTBOUND == this.type || InterfaceTypes.INOUTBOUND == this.type) return true;
		return false;
	}

	/**
	 * After a successful or unsuccessful attempt to send a message, SMSServer
	 * calls this method. The interface can then decide what to do with the
	 * message. Note that the message status and errors member fields are
	 * updated, so you should examine them in order to determine whether the
	 * message has been sent out, etc.
	 * 
	 * @param msg
	 *            The Outbound message.
	 * @throws Exception
	 */
	public abstract void markMessage(OutboundMessage msg) throws Exception;

	public void markMessages(Collection<OutboundMessage> msgList) throws Exception
	{
		for (OutboundMessage msg : msgList)
			markMessage(msg);
	}

	/**
	 * This method is called by SMSServer every time a message (or more
	 * messages) is received. SMSServer calls this method for all
	 * available/active interfaces.
	 * 
	 * @param msgList
	 *            A message list of all received messages.
	 * @throws Exception
	 */
	public abstract void MessagesReceived(Collection<InboundMessage> msgList) throws Exception;

	/**
	 * Called once before SMSServer starts its operation. Use this method for
	 * initialization.
	 * 
	 * @throws Exception
	 *             An exception thrown will stop SMSServer from starting its
	 *             processing.
	 */
	public abstract void start() throws Exception;

	/**
	 * Called once after SMSServer has finished. Use this method for cleaning up
	 * your interface.
	 * 
	 * @throws Exception
	 */
	public abstract void stop() throws Exception;
}
