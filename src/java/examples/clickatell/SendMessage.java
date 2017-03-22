// SendMessage.java - Sample application.
//
// This application shows you the basic procedure for sending messages.
// You will find how to send synchronous and asynchronous messages.
//
// For asynchronous dispatch, the example application sets a callback
// notification, to see what's happened with messages.
//
// Bulk Operator used: Clickatell (http://www.clickatell.com)
// Please look the ClickatellHTTPGateway documentation for details.

package examples.clickatell;

import java.net.Authenticator;
import java.net.URLEncoder;
import java.util.Properties;

import org.smslib.IOutboundMessageNotification;
import org.smslib.Library;
import org.smslib.OutboundMessage;
import org.smslib.Service;
import org.smslib.http.ClickatellHTTPGateway;
import org.smslib.http.HTTPGateway;
import org.smslib.http.RouteToHTTPGateway;
import org.smslib.http.VoiceTradingHTTPGateway;

public class SendMessage
{
	public void doIt(String args[]) throws Exception {		
		Service srv;
		OutboundMessage msg;
		OutboundNotification outboundNotification = new OutboundNotification();
		System.out.println("Example: Send message from Clickatell HTTP Interface.");
		System.out.println(Library.getLibraryDescription());
		System.out.println("Version: " + Library.getLibraryVersion());
		srv = new Service();
		HTTPGateway gateway = null;

		if (args[0].equalsIgnoreCase("Clickatell"))
			//gateway = new ClickatellHTTPGateway("clickatell.http.1", "api_id", "username", "password");
			gateway = new ClickatellHTTPGateway("clickatell.http.1", args[1], args[2], args[3]);
		else if (args[0].equalsIgnoreCase("VoiceTrading"))
			gateway = new VoiceTradingHTTPGateway("Test", args[1],	args[2]);
		else
			if (args[0].equalsIgnoreCase("RoutoSMS"))
				gateway = new RouteToHTTPGateway("Test", args[1],	args[2]);
		gateway.setOutbound(true);
		srv.setOutboundNotification(outboundNotification);
		// Do we need secure (https) communication?
		// True uses "https", false uses "http" - default is false.
		gateway.setSecure(true);
		srv.addGateway(gateway);
		srv.startService();
		// Create a message.
		//msg = new OutboundMessage(args[3], URLEncoder.encode("Hello from SMSLib", "UTF-8"));
		msg = new OutboundMessage(args[3], "Hello from SMSLib");
		msg.setFrom(args[0]);
		// Ask for coverage.
//		System.out.println("Is recipient's network covered? : " + gateway.queryCoverage(msg));
		// Send the message.
		srv.sendMessage(msg);
		System.out.println(msg);
		// Now query the service to find out our credit balance.
		//System.out.println("Remaining credit: " + gateway.queryBalance());
		// Send some messages in async mode...
		// After this, your IOutboundMessageNotification method will be called
		// for each message sent out.
		//msg = new OutboundMessage("+30...", "Max");
		//srv.queueMessage(msg, "clickatell.http.1", AbstractGateway.Priority.HIGH);
		//msg = new OutboundMessage("+30...", "Min");
		//srv.queueMessage(msg, "clickatell.http.1", AbstractGateway.Priority.LOW);
		System.out.println("Now Sleeping - Hit <enter> to terminate.");
		System.in.read();
		srv.stopService();
	}

	public class OutboundNotification implements IOutboundMessageNotification
	{
		public void process(String gatewayId, OutboundMessage msg)
		{
			System.out.println("Outbound handler called from Gateway: " + gatewayId);
			System.out.println(msg);
		}
	}

	public static void main(String args[])
	{		
		SendMessage app = new SendMessage();
		try
		{
			app.doIt(args);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
