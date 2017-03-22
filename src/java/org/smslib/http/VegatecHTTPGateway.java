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

public class VegatecHTTPGateway extends HTTPGateway {

	Object SYNC_Commander;

	String HTTP = "http://";

	String URL_SENDMSG = "vegatecsms:8081/smswebservice/";

	public VegatecHTTPGateway(String id, String myUsername, String myPassword)
	{
		super(id);
		this.secure = false;
		this.SYNC_Commander = new Object();
		setAttributes(AGateway.GatewayAttributes.SEND | AGateway.GatewayAttributes.WAPSI | AGateway.GatewayAttributes.CUSTOMFROM | AGateway.GatewayAttributes.BIGMESSAGES | AGateway.GatewayAttributes.FLASHSMS);
	}

	public VegatecHTTPGateway(String id, String myUsername, String myPassword, String url)
	{
		super(id);
		this.secure = false;
		this.SYNC_Commander = new Object();
		setAttributes(AGateway.GatewayAttributes.SEND | AGateway.GatewayAttributes.WAPSI | AGateway.GatewayAttributes.CUSTOMFROM | AGateway.GatewayAttributes.BIGMESSAGES | AGateway.GatewayAttributes.FLASHSMS);
		URL_SENDMSG = url;
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
		request.add(new HttpHeader("username", null, false));
		request.add(new HttpHeader("password", null, false));
		if (msg.getFrom() != null && msg.getFrom().length() != 0)
			request.add(new HttpHeader("from", removeSpecialCharacters(msg.getFrom()), false));
		else {
			msg.setRefNo(null);
			msg.setDispatchDate(null);
			msg.setFailureCause(FailureCauses.BAD_NUMBER);
			msg.setMessageStatus(MessageStatuses.FAILED);
			return false;
		}
//		if (msg.getFrom() != null && msg.getFrom().length() != 0) request.add(new HttpHeader("from", msg.getFrom(), false));
//		else if (getFrom() != null && getFrom().length() != 0) request.add(new HttpHeader("from", getFrom(), false));
		request.add(new HttpHeader("to", removeSpecialCharacters(msg.getRecipient()), false)); // To remove the +
		request.add(new HttpHeader("text", msg.getText(), false));
//		request.add(new HttpHeader("allow_concat_text_sms", "1", false));
		//if (msg.getStatusReport()) request.add(new HttpHeader("want_report", "1", false));
		//if (msg.getFlashSms()) request.add(new HttpHeader("msg_class", "0", false));
		reqLine = ExpandHttpHeaders(request);
		reqLine = reqLine.substring(0, reqLine.length() - 1);
		url = new URL((!(URL_SENDMSG.startsWith("http"))?HTTP:"") + URL_SENDMSG + "?" + reqLine);
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
		
		String result = getValue("result", allres);
		if (result.equals("0"))
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
			String endCause = getValue("endCause", allres);
			if (endCause == null)
				endCause = "23"; // Authentication failure
			if (endCause != null)
				switch (Integer.parseInt(endCause))
				{
					case 1: // Not enough credit
						msg.setFailureCause(FailureCauses.NO_CREDIT);
						break;
					case 19: // Invalid number
						msg.setFailureCause(FailureCauses.BAD_FORMAT);
						break;
					case 23:
						msg.setFailureCause(FailureCauses.GATEWAY_AUTH);
						break;
				}
			msg.setRefNo(null);
			msg.setDispatchDate(null);
			msg.setMessageStatus(MessageStatuses.FAILED);
			ok = false;
		}
		return ok;
	}
	
	private String getValue(String attribute, String inputText) {
		String value;
		int start = inputText.indexOf("<" + attribute + ">") + attribute.length() + 2;
		if (start < 0)
			return null;
		int end = inputText.indexOf("</" + attribute + ">");
		if (end < 0)
			return null;

		value = inputText.substring(start, end);
		return value;
	}
}
