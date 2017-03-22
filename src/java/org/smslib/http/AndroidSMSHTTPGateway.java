package org.smslib.http;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.smslib.AGateway;
import org.smslib.GatewayException;
import org.smslib.OutboundMessage;
import org.smslib.TimeoutException;
import org.smslib.OutboundMessage.FailureCauses;
import org.smslib.OutboundMessage.MessageStatuses;

public class AndroidSMSHTTPGateway extends HTTPGateway {

	Object SYNC_Commander;

	String HTTP = "http://";

	String URL_SENDMSG = "192.168.0.18:9090/sendsms";
	String userName = null;
	String password = null;	
	
	public AndroidSMSHTTPGateway(String id, String myUsername, String myPassword)
	{
		super(id);
		this.secure = false;
		this.SYNC_Commander = new Object();
		userName = myUsername;
		password = myPassword;
		setAttributes(AGateway.GatewayAttributes.SEND | AGateway.GatewayAttributes.WAPSI | AGateway.GatewayAttributes.CUSTOMFROM | AGateway.GatewayAttributes.BIGMESSAGES | AGateway.GatewayAttributes.FLASHSMS);
	}

	public AndroidSMSHTTPGateway(String id, String myUsername, String myPassword, String url)
	{
		super(id);
		this.secure = false;
		this.SYNC_Commander = new Object();
		userName = myUsername;
		password = myPassword;
		URL_SENDMSG = url;
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

	String removeSpecialCharacters(String phoneNumber) {
		String newString = "";
		for (int i=0; i < phoneNumber.length(); i++) {
			char cur = phoneNumber.charAt(i);
			if ((cur >= '0') && (cur <='9')) {
				newString += cur;
			}
		}
		return newString;
	}
	
	@SuppressWarnings("unused")
	@Override
	public boolean sendMessage(OutboundMessage msg) throws TimeoutException, GatewayException, IOException, InterruptedException
	{
		URL url = null;
		List<HttpHeader> request = new ArrayList<HttpHeader>();
		List<String> response;
		String reqLine;
		boolean ok = false;
		request.add(new HttpHeader("password", password, false));
		request.add(new HttpHeader("phone", removeSpecialCharacters(msg.getRecipient()), false)); // To remove the +
		request.add(new HttpHeader("text", msg.getText(), false));
		reqLine = ExpandHttpHeaders(request);
		reqLine = reqLine.substring(0, reqLine.length() - 1);
		url = new URL(HTTP + URL_SENDMSG + "?" + reqLine);
		getService().getLogger().logInfo("URL is " + url, null, getGatewayId());
		System.out.println("URL is " + url);
		
		synchronized (this.SYNC_Commander)
		{
			response = HttpGet(url);
		}
		String allres = "";
		for (int i = 0; i < response.size(); i++) {
			allres += response.get(i);
		}
		
		System.out.println("Result = " + allres);
		
		if (allres.contains("SENT"))
		{
			//msg.setRefNo(msg.getRefNo());
			msg.setDispatchDate(new Date());
			msg.setGatewayId(getGatewayId());
			msg.setMessageStatus(MessageStatuses.SENT);
			incOutboundMessageCount();
			ok = true;
		}
		else
		{
			if (allres.contains("Invalid password"))
				msg.setFailureCause(FailureCauses.GATEWAY_AUTH);
			else
				msg.setFailureCause(FailureCauses.BAD_FORMAT);
			msg.setRefNo(null);
			msg.setDispatchDate(null);
			msg.setMessageStatus(MessageStatuses.FAILED);
			ok = false;
		}
		return ok;
	}
	
}
