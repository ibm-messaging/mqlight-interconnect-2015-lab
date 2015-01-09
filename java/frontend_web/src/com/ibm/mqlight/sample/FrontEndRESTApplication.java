// ******************************************************************
//
// Program name: mqlight_sample_frontend_web
//
// Description:
//
// A http servlet that demonstrates use of the IBM Bluemix MQ Light Service.
//
// <copyright
// notice="lm-source-program"
// pids=""
// years="2014"
// crc="659007836" >
// Licensed Materials - Property of IBM
//
//
// (C) Copyright IBM Corp. 2014 All Rights Reserved.
//
// US Government Users Restricted Rights - Use, duplication or
// disclosure restricted by GSA ADP Schedule Contract with
// IBM Corp.
// </copyright>
// *******************************************************************

package com.ibm.mqlight.sample;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.ibm.json.java.JSONObject;

/**
 * This file forms part of the MQ Light Sample Messaging Application - Worker offload pattern sample
 * provided to demonstrate the use of the IBM Bluemix MQ Light Service.
 * 
 * It provides a simple JAX-RS REST service that:
 * - Publishes messages to a back-end worker upon a REST POST
 * - Consumes notifications from the back-end asynchronously upon a REST GET
 * 
 * The REST interface is very simple, using plain text.
 * The posted text is split into separate words, and each sent to the back-end in a separate message.
 * The responses are returned one at a time to REST GET requests. 
 */
/**
 * Servlet implementation class FrontEndServlet
 */
@ApplicationPath("/rest/")
@Path("/")
public class FrontEndRESTApplication extends Application {
	
	/** The name of the MQ Light service you have deployed.
	 * Unless you are pushing a server.xml, the following applies:
	 * - MUST match the resource reference name in your web.xml (prefixed by 'jms/')
	 * - MUST match the services entry in your manifest.yml when you deploy.*/
	private static final String MQLIGHT_SERVICE_NAME = "MQLight-sampleservice";

	/** The topic we publish on to send data to the back-end */
	private static final String PUBLISH_TOPIC = "mqlight/sample/words";
	
	/** The topic we subscribe on to receive notifications from the back-end */
	private static final String SUBSCRIBE_TOPIC = "mqlight/sample/wordsuppercase";
	
	/** The name of our durable subscription */
	private static final String SUBSCRIPTION_NAME = "mqlight.sample.subscription";
	
	/** Our connection factory */ 
    private final ConnectionFactory mqlightCF;
	
    /** Simple logging */
	private final static Logger logger = Logger.getLogger(FrontEndRESTApplication.class.getName());
	
	/** JVM-wide initialisation of our subscription */
	private static boolean subInitialised = false;
	
    /**
     * Default Constructor
     */
    public FrontEndRESTApplication() {
    	// As JAX-RS does not support dependency injection, we need to lookup
    	// our resource reference using an initial context here, and include
    	// the resource reference in our web.xml
        logger.log(Level.INFO,"Initialising...");
        
        try {
    		InitialContext ctx = new InitialContext();
    		mqlightCF = (ConnectionFactory)ctx.lookup("java:comp/env/jms/" + MQLIGHT_SERVICE_NAME);
    		logger.log(Level.INFO, "Connection factory successfully created");
    		ctx.close();
    	}
    	catch (NamingException e) {
    		logger.log(Level.SEVERE, "Failed to initialise", e);
    		throw new RuntimeException(e);
    	}
        logger.log(Level.INFO,"Completed initialisation.");

    }

    /**
     * POST on the words resource publishes the content of the POST to our topic
     */
    @POST
    @Path("words")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response publishWords(Map<String, String> jsonInput) {
        
        logger.log(Level.INFO,"Publishing words" + jsonInput);
    	
    	// Check the caller supplied some words
    	String words = jsonInput.get("words");
    	if (words == null) throw new RuntimeException("No words sent");
        
    	// Before we connect to publish, we need to ensure our subscription has been
    	// initialised, otherwise we might miss responses. 
    	checkSubInitialised(mqlightCF);
    	
    	Connection jmsConn = null;
    	try {
	    	// Connect to the service using the CF from our resource reference
	    	jmsConn = mqlightCF.createConnection();

	    	// Create a session.
	    	Session jmsSess = jmsConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	    	
	    	// Create a producer on our topic
	    	Destination publishDest = jmsSess.createTopic(PUBLISH_TOPIC);
	    	MessageProducer producer = jmsSess.createProducer(publishDest);
	    	
	    	// Set an expiry on our messages
	    	producer.setTimeToLive(60*1000);
	    	
	    	// We send a separate message for each word in the request
	    	StringTokenizer strtok = new StringTokenizer(words, " ");
	    	int tokens = strtok.countTokens();
	    	while (strtok.hasMoreTokens()) {
	    		// Create the JSON payload
	    		JSONObject jsonPayload = new JSONObject();
	    		jsonPayload.put("word", strtok.nextToken());
	    		jsonPayload.put("frontend", "LibertyJava: " + toString());
	    		
		    	// Create our message
		    	TextMessage textMessage = jmsSess.createTextMessage(jsonPayload.serialize());
	    		
	    		// Send it
		        logger.log(Level.INFO,"Sending message " + textMessage.getText());
		    	producer.send(textMessage);
	    	}
	    	
	    	// Cleanup
	    	producer.close();
	    	jmsSess.close();
	    	
	    	// Send back a message count
	    	HashMap<String, Integer> jsonOutput = new HashMap<>();
	    	jsonOutput.put("msgCount", tokens);
	    	return Response.ok(jsonOutput).build();	    	
    	}
    	catch (Exception e) {
    		logger.log(Level.SEVERE, "Exception sending message to MQ Light", e);
    		throw new RuntimeException(e);
    	}
    	finally {
    		// Ensure we cleanup our connection
    		try {
    			if (jmsConn != null) jmsConn.close();
    		}
    		catch (Exception e) {
        		logger.log(Level.SEVERE, "Exception closing connection to MQ Light", e);
    		}
    	}
    }
    
    /**
     * GET on the wordsuppercase resource checks for any publications that have been
     * returned on our subscription. Replies with either a single word, or a 204 (No Content).
     * Call repeatedly to get all the responses
     */
    @GET
    @Path("wordsuppercase")
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkForPublications() {
    	// Delegate to a static method synchronized across the JVM
    	return singleThreadedCheckForPublications(mqlightCF);
    }
    
	/**
	 * We have a static synchronized method for consuming messages from our subscription.
	 * This is because multiple threads within a single JVM cannot access the same
	 * durable subscription concurrently.
	 * If they did, one of the threads would receive an error (JMSWMQ2011).
	 */
    private static synchronized Response singleThreadedCheckForPublications(ConnectionFactory mqlightCF) {
        Response response;
    	Connection jmsConn = null;
    	try {
	    	// Connect to the service using the CF from our resource reference
	    	jmsConn = mqlightCF.createConnection();

	    	// Create a session.
	    	Session jmsSess = jmsConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	    	
	    	// Access our subscription
	    	Topic notificationDest = jmsSess.createTopic(SUBSCRIBE_TOPIC);
	    	MessageConsumer consumer =
	    			jmsSess.createDurableSubscriber(notificationDest, SUBSCRIPTION_NAME);
	    	
	    	// Issue 'start' on the connection so we can receive messages
	        jmsConn.start();
	    	
	    	// Do a non-blocking receive to check for any messages
	    	Message message = consumer.receiveNoWait();
	    	if (message instanceof TextMessage) {
	    		TextMessage textMessage = (TextMessage)message;
	    		
	    		// Parse the JSON in the message
	    		JSONObject jsonPayload = JSONObject.parse(textMessage.getText());
	    		response = Response.ok(jsonPayload).build();
	    		logger.log(Level.INFO, "Received response " + jsonPayload);
	    	}
	    	// Check we didn't get another type of message
	    	else if (message != null) {
	    		throw new RuntimeException("Invalid message type: " + message.getJMSType());
	    	}
	    	// Otherwise there weren't any messages available
	    	else {
	    		response = Response.status(204).build();
	    	}
	    	
	    	// Cleanup
	    	consumer.close();
	    	jmsSess.close();	    	
    	}
    	catch (Exception e) {
    		logger.log(Level.SEVERE, "Exception sending message to MQ Light", e);
    		throw new RuntimeException(e);
    	}
    	finally {
    		// Ensure we cleanup our connection
    		try {
    			if (jmsConn != null) jmsConn.close();
    		}
    		catch (Exception e) {
        		logger.log(Level.SEVERE, "Exception closing connection to MQ Light", e);
    		}
    	}
    	return response;    	
    }

    /**
     * Before we send any messages, we need to ensure our subscription has been initialised
     * @param mqlightCF
     * @return
     */
    private static synchronized void checkSubInitialised(ConnectionFactory mqlightCF) {
    	if (subInitialised) return;
    	Connection jmsConn = null;
    	try {
	    	// Connect to the service using the CF from our resource reference
	    	jmsConn = mqlightCF.createConnection();

	    	// Create a session
	    	Session jmsSess = jmsConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	    	
	    	// Access our subscription - we don't need to do anything more than that
	    	Topic notificationDest = jmsSess.createTopic(SUBSCRIBE_TOPIC);
	    	MessageConsumer consumer =
	    			jmsSess.createDurableSubscriber(notificationDest, SUBSCRIPTION_NAME);
	    	
	    	// Cleanup
	    	consumer.close();
	    	jmsSess.close();
	    	
	    	// We're done
	    	subInitialised = true;
	    	logger.log(Level.INFO, "Subscription correctly initialised");
    	}
    	catch (Exception e) {
    		logger.log(Level.SEVERE, "Exception initialising subscription with MQ Light", e);
    		throw new RuntimeException(e);
    	}
    	finally {
    		// Ensure we cleanup our connection
    		try {
    			if (jmsConn != null) jmsConn.close();
    		}
    		catch (Exception e) {
        		logger.log(Level.SEVERE, "Exception closing connection to MQ Light", e);
    		}
    	}
    }
    
}
