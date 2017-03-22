package org.smslib.http;

import java.io.IOException;
import java.util.Date;

import org.smslib.AGateway;
import org.smslib.GatewayException;
import org.smslib.OutboundMessage;
import org.smslib.TimeoutException;
import org.smslib.OutboundMessage.FailureCauses;
import org.smslib.OutboundMessage.MessageStatuses;

public class RouteToHTTPGateway extends HTTPGateway {
	String apiId, username, password;

	String sessionId;

	boolean secure;
	
	Object SYNC_Commander;
	
	public RouteToHTTPGateway(String id, String myUsername, String myPassword)
	{
		super(id);
		this.username = myUsername;
		this.password = myPassword;
		this.sessionId = null;
		this.secure = true;
		this.SYNC_Commander = new Object();
		setAttributes(AGateway.GatewayAttributes.SEND | AGateway.GatewayAttributes.WAPSI | AGateway.GatewayAttributes.CUSTOMFROM | AGateway.GatewayAttributes.BIGMESSAGES | AGateway.GatewayAttributes.FLASHSMS);
	}

	/**
	 * Sets whether the gateway works in unsecured (HTTP) or secured (HTTPS)
	 * mode. False denotes unsecured.
	 * 
	 * @param mySecure
	 *            True for HTTPS, false for plain HTTP.
	 */
	public void setSecure(boolean mySecure)
	{
		this.secure = mySecure;
	}

	/**
	 * Return the operation mode (HTTP or HTTPS).
	 * 
	 * @return True for HTTPS, false for HTTP.
	 * @see #setSecure(boolean)
	 */
	public boolean getSecure()
	{
		return this.secure;
	}

	@Override
	public void startGateway() throws TimeoutException, GatewayException, IOException, InterruptedException
	{
		getService().getLogger().logInfo("Starting gateway.", null, getGatewayId());
		super.startGateway();
	}

	@Override
	public void stopGateway() throws TimeoutException, GatewayException, IOException, InterruptedException
	{
		getService().getLogger().logInfo("Stopping gateway.", null, getGatewayId());
		super.stopGateway();
	}


	@SuppressWarnings("unused")
	@Override
	public boolean sendMessage(OutboundMessage msg) throws TimeoutException, GatewayException, IOException, InterruptedException
	{
		RoutoTelecomSMS request = new RoutoTelecomSMS();
		request.SetUser(username);
		request.SetPass(password);
		request.SetType("SMS");
		if (msg.getFrom() != null && msg.getFrom().length() != 0)
			request.SetOwnNumber(msg.getFrom());
		else {
			msg.setRefNo(null);
			msg.setDispatchDate(null);
			msg.setFailureCause(FailureCauses.BAD_NUMBER);
			msg.setMessageStatus(MessageStatuses.FAILED);
			return false;
		}

		request.SetNumber(msg.getRecipient());
		request.SetMessage(msg.getText());

		String response = "";
		boolean ok = false;
		synchronized (this.SYNC_Commander)
		{
			response = request.Send();
			getService().getLogger().logInfo("Result = " + response, null, getGatewayId());			
			//System.out.println("Result = " + response);			
		}
		
		if (response == null) {
			System.out.println("Result = " + response);
			msg.setRefNo(null);
			msg.setDispatchDate(null);
			msg.setMessageStatus(MessageStatuses.FAILED);
			ok = false;
		} else if (response.equalsIgnoreCase("Success")) {
			//msg.setRefNo(msg.getRefNo());
			msg.setDispatchDate(new Date());
			msg.setGatewayId(getGatewayId());
			msg.setMessageStatus(MessageStatuses.SENT);
			incOutboundMessageCount();
			ok = true;
		}else {
			System.out.println("Failed = " + response);			
			if (response.equalsIgnoreCase("error")) // Not all required parameters are present
				msg.setFailureCause(FailureCauses.UNKNOWN);
			else if (response.equalsIgnoreCase("auth_failed")) // Incorrect username and/or password
				msg.setFailureCause(FailureCauses.GATEWAY_AUTH);
			else if (response.equalsIgnoreCase("wrong_number")) // The number contains non-numeric characters
				msg.setFailureCause(FailureCauses.BAD_NUMBER);
			else if (response.equalsIgnoreCase("Not_allowed")) // You are not allowed to send to this number
				msg.setFailureCause(FailureCauses.GATEWAY_AUTH);
			else if (response.equalsIgnoreCase("Too_many_numbers")) // Sending to more than 10 numbers per request
				msg.setFailureCause(FailureCauses.BAD_FORMAT);
			else if (response.equalsIgnoreCase("no_message")) // No message given
				msg.setFailureCause(FailureCauses.BAD_FORMAT);
			else if (response.equalsIgnoreCase("Too_long")) // Message is too long
				msg.setFailureCause(FailureCauses.BAD_FORMAT);
			else if (response.equalsIgnoreCase("wrong_type")) // An Incorrect message type was selected
				msg.setFailureCause(FailureCauses.BAD_FORMAT);
			else if (response.equalsIgnoreCase("wrong_message")) // vCalendar contains wrong message
				msg.setFailureCause(FailureCauses.BAD_FORMAT);
			else if (response.equalsIgnoreCase("wrong_format")) // The wrong message format was selected
				msg.setFailureCause(FailureCauses.BAD_FORMAT);
			else if (response.equalsIgnoreCase("Bad_operator")) // Wrong operator code
				msg.setFailureCause(FailureCauses.BAD_NUMBER);
			else if (response.equalsIgnoreCase("failed")) // Internal error
				msg.setFailureCause(FailureCauses.GATEWAY_FAILURE);
			else if (response.equalsIgnoreCase("Sys_error")) // The system error
				msg.setFailureCause(FailureCauses.GATEWAY_FAILURE);
			else if (response.equalsIgnoreCase("No Credits Left")) // user has no credits
				msg.setFailureCause(FailureCauses.NO_CREDIT);
			else if (response.equalsIgnoreCase("No_Credits_Left")) // user has no credits
				msg.setFailureCause(FailureCauses.NO_CREDIT);
			 
			msg.setRefNo(null);
			msg.setDispatchDate(null);
			msg.setMessageStatus(MessageStatuses.FAILED);
			ok = false;
		}
		return ok;
	}	
}
