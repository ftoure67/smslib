package org.smslib.http;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import org.smslib.AGateway;
import org.smslib.GatewayException;
import org.smslib.OutboundMessage;
import org.smslib.OutboundWapSIMessage;
import org.smslib.TimeoutException;
import org.smslib.Message.MessageEncodings;
import org.smslib.Message.MessageTypes;
import org.smslib.OutboundMessage.FailureCauses;
import org.smslib.OutboundMessage.MessageStatuses;
import org.smslib.OutboundWapSIMessage.WapSISignals;
import org.smslib.StatusReportMessage.DeliveryStatuses;
import org.smslib.http.ClickatellHTTPGateway.KeepAlive;
import org.smslib.http.HTTPGateway.HttpHeader;

public class VoiceTradingHTTPGateway extends HTTPGateway {

	String apiId, username, password;

	String sessionId;

	boolean secure;

	Object SYNC_Commander;

	String HTTPS = "https://";

	String URL_SENDMSG = "www.voicetrading.com/myaccount/sendsms.php";

	public VoiceTradingHTTPGateway(String id, String myUsername, String myPassword)
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
		URL url = null;
		List<HttpHeader> request = new ArrayList<HttpHeader>();
		List<String> response;
		String reqLine;
		boolean ok = false;
		request.add(new HttpHeader("username", this.username, false));
		request.add(new HttpHeader("password", this.password, false));
		if (msg.getFrom() != null && msg.getFrom().length() != 0)
			request.add(new HttpHeader("from", msg.getFrom(), false));
		else {
			msg.setRefNo(null);
			msg.setDispatchDate(null);
			msg.setFailureCause(FailureCauses.BAD_NUMBER);
			msg.setMessageStatus(MessageStatuses.FAILED);
			return false;
		}
//		if (msg.getFrom() != null && msg.getFrom().length() != 0) request.add(new HttpHeader("from", msg.getFrom(), false));
//		else if (getFrom() != null && getFrom().length() != 0) request.add(new HttpHeader("from", getFrom(), false));
		request.add(new HttpHeader("to", msg.getRecipient()/*.substring(1)*/, false));
		request.add(new HttpHeader("text", msg.getText(), false));
//		request.add(new HttpHeader("allow_concat_text_sms", "1", false));
		//if (msg.getStatusReport()) request.add(new HttpHeader("want_report", "1", false));
		//if (msg.getFlashSms()) request.add(new HttpHeader("msg_class", "0", false));
		reqLine = ExpandHttpHeaders(request);
		reqLine = reqLine.substring(0, reqLine.length() - 1);
		url = new URL(HTTPS + URL_SENDMSG + "?" + reqLine);
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
		if (result.equals("1"))
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
