package org.smslib.http;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import examples.clickatell.SimpleAuthenticator;

import java.io.*;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

import org.apache.log4j.Logger;

public class RoutoTelecomSMS {
		static Logger log = Logger.getLogger("org.smslib.http.RoutoTelecomSMS");

         public String user;
         public String pass;
         public String number;
         public String ownnum;
         public String message;
         public String messageId;
         public String type;
         public String model;
         public String op;
         String PROXY_FILE = "proxy.properties";
         String routoSMSUrl = "http://smsc6.routotelecom.com/cgi-bin/SMSsend"; 
         int _timeout = 10000;

         public RoutoTelecomSMS() {
        	 user = "";
        	 pass = "";
        	 number = "";
        	 ownnum = "";
        	 message = "";
        	 messageId = "";
        	 type = "";
        	 model = "";
        	 op = "";
         }

         public String SetUser(String s) {
        	 user = s;
        	 return user;
         }

         public String SetPass(String s) {
        	 pass = s;
        	 return pass;
         }

         public String SetNumber(String s) {
        	 number = s;
        	 return number;
         }

         public String SetOwnNumber(String s) {
        	 ownnum = s;
        	 return ownnum;
         }

         public String SetType(String s) {
        	 type = s;
        	 return type;
         }

         public String SetModel(String s) {
        	 model = s;
        	 return model;
         }

         public String SetMessage(String s) {
        	 message = s;
        	 return message;
         }

         public String SetMessageId(String s) {
        	 messageId = s;
        	 return messageId;
         }

         public String SetOp(String s) {
        	 op = s;
        	 return op;
         }

         public String MIMEEncode(byte abyte0[]) {
        	 return Base64.encode(abyte0);
         }

         public byte[] convertStringToByteArray(String s) {
        	 byte abyte0[] = s.getBytes();
        	 return abyte0;
         }

         public String getImage(String s) {
        	 String s1 = "";
        	 String s2 = "";
        	 String s4 = "";
        	 Object obj = null;
        	 try {
        		 URL url = new URL(s);
        		 BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(url.openStream()));
                 String s3;
                 while ((s3 = bufferedreader.readLine()) != null)  {
                	 s4 = (new StringBuilder()).append(s4).append(s3).toString();
                 }
                 bufferedreader.close();
                 byte abyte0[] = convertStringToByteArray(s4);
                 s1 = MIMEEncode(abyte0);
             }
        	 catch (Exception exception) { }
        	 return s1;
         }

     	private Properties loadConfig(String fileName) throws FileNotFoundException, IOException {		
    		Properties tempConfig = new Properties();
    		tempConfig.load(new FileInputStream(fileName));
    		return tempConfig;
    	}
         
     	private HttpURLConnection getXMLConnection(String url) {		
    		URL servlet;
    		try {
    			servlet = new URL(url);
    			//System.out.println("URL = " + url);
    			log.info("URL = " + url);
    			HttpURLConnection XMLConnection = (HttpURLConnection) servlet.openConnection();
    			XMLConnection.setDoOutput(true);
    		    File f = new File(PROXY_FILE);	    
    		    boolean exists = f.exists();
    	
    			if (exists) {
    				Properties config = loadConfig(PROXY_FILE);
    				Authenticator.setDefault(new SimpleAuthenticator(config.getProperty("http.proxyLogin"),config.getProperty("http.proxyPassword")));
    				Properties systemProperties = System.getProperties();
    				systemProperties.putAll(config);
    			}
    			//XMLConnection.setRequestProperty("Authorization", "Basic " + pass);
    			
    			XMLConnection.setRequestMethod("POST");
    			XMLConnection.setRequestProperty("Accept","text-xml");
    			XMLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    	
    			XMLConnection.setConnectTimeout(_timeout);
    			XMLConnection.setReadTimeout(_timeout);
    			return XMLConnection;
    		} catch (MalformedURLException e) {
    			System.out.println(e.getMessage());
    			e.printStackTrace();
    			return null;
    		} catch (ProtocolException e) {
    			System.out.println(e.getMessage());
    			e.printStackTrace();
    			return null;
    		} catch (FileNotFoundException e) {
    			System.out.println(e.getMessage());
    			e.printStackTrace();
    			return null;
    		} catch (IOException e) {
    			System.out.println(e.getMessage());
    			e.printStackTrace();
    			return null;
    		}
    	}
    	
    	protected String sendRequest(HttpURLConnection xmlConnection, String xmlInputString) throws Exception {
    		try {
    			xmlConnection.connect();
    			PrintWriter out = new PrintWriter(xmlConnection.getOutputStream());
    	
    			// Here's whether the parameter is set.
    			out.print(xmlInputString);
    			out.close();
    			xmlConnection.connect();
    	
    			System.out.println("HTTP Response : " + xmlConnection.getResponseMessage());
    			InputStream in = xmlConnection.getInputStream();
    			//System.out.println("Content length = " + xmlConnection.getContentLength());
    			//System.out.println("IN Available = " + in.available());
    			
    			StringBuffer contentBuffer = new StringBuffer();
    			byte[] buffer = new byte[1024];
    			int byteCount;
    			do {
    				byteCount = in.read(buffer, 0, 1024);
    				if( byteCount != -1 ) {
    					String line = new String(buffer, 0, byteCount);
    					contentBuffer.append(line);
    				}
    			} while( byteCount != -1 );
    			in.close();
    			String xmlResponse = new String(contentBuffer);
    			xmlResponse.replace('\n', ' ');
    			xmlResponse = xmlResponse.trim();
    			log.info("Response = " + xmlResponse);
    			System.out.println("Response = " + xmlResponse);
    			
    			//xmlConnection.disconnect();
    			return xmlResponse;
    		} catch (IOException e) {
    			try {
    				int respCode = ((HttpURLConnection)xmlConnection).getResponseCode();
    				System.out.println("Response code = " + respCode);
    				InputStream es = ((HttpURLConnection)xmlConnection).getErrorStream();
    				int ret = 0;
    				
    				// read the response body
    				StringBuffer contentBuffer = new StringBuffer();
    				byte[] buffer = new byte[1024];
    				int byteCount;
    				do {
    					byteCount = es.read(buffer, 0, 1024);
    					if( byteCount != -1 ) {
    						String line = new String(buffer, 0, byteCount);
    						contentBuffer.append(line);
    					}
    				} while( byteCount != -1 );
    				// close the errorstream
    				es.close();
    				String xmlResponse = new String(contentBuffer);
        			log.info("Response error = " + xmlResponse);
        			System.out.println("Response error = " + xmlResponse);
    				return xmlResponse;
    			} catch(IOException ex) {
    				throw ex;
    			}
    		}
    	}
    	
    	protected String sendRequest(String url, String request) throws Exception {
    		String response = null;

    		try {
    			HttpURLConnection xmlConnection = getXMLConnection(url);
    			response = sendRequest(xmlConnection, request);
    		} catch (Exception e) {
    			System.out.println("Exception sending the request on the server " + url);
    		} finally {
    		}

    		return response;
    	}
         
         public String urlencode(String s) {
        	 return URLEncoder.encode(s);
         }

         public String Send() {
        	 String s = "";
        	 s = (new StringBuilder()).append(s).append("number=").append(number).toString();
        	 s = (new StringBuilder()).append(s).append("&user=").append(urlencode(user)).toString();
        	 s = (new StringBuilder()).append(s).append("&pass=").append(urlencode(pass)).toString();
        	 s = (new StringBuilder()).append(s).append("&message=").append(urlencode(message)).toString();
        	 if (messageId.length() >= 1) {
        		 s = (new StringBuilder()).append(s).append("&mess_id=").append(urlencode(messageId)).append("&delivery=1").toString();
             }
        	 if (ownnum != "") {
        		 s = (new StringBuilder()).append(s).append("&ownnum=").append(urlencode(ownnum)).toString();
             }
        	 if (model != "") {
        		 s = (new StringBuilder()).append(s).append("&model=").append(urlencode(model)).toString();
             }
        	 if (op != "") {
        		 s = (new StringBuilder()).append(s).append("&op=").append(urlencode(op)).toString();
             }
        	 if (type != "") {
        		 s = (new StringBuilder()).append(s).append("&type=").append(urlencode(type)).toString();
             }
        	 int i = s.length();
        	 String s3 = null;
        	 try {
        		 
        		 s3 = sendRequest(routoSMSUrl, s);
             }
        	 catch (IOException ioexception) {
        		 System.err.println("Couldn't get I/O for the connection to server.");
             } catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	 return s3;
         }

         public String GetUser() {
        	 return user;
         }

         public String GetPass() {
        	 return pass;
         }

         public String GetNumber() {
        	 return number;
         }

         public String GetMessage() {
        	 return message;
         }

         public String GetMessageId() {
        	 return messageId;
         }

         public String GetOwnNum() {
        	 return ownnum;
         }

         public String GetOp() {
        	 return op;
         }

         public String GetType() {
        	 return type;
         }

         public String GetModel() {
        	 return model;
         }
}
