// ******************************************************************
//
// Program name: mqlight_sample_backend_mdb
//
// Description:
//
// An MDB that demonstrates use of the IBM Bluemix MQ Light Service.
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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import com.ibm.json.java.JSONObject;

/**
 * Message-Driven Bean implementation class for: BackEndMDB
 */
@MessageDriven(
		activationConfig = { @ActivationConfigProperty(
				propertyName = "destinationType", propertyValue = "javax.jms.Topic"), @ActivationConfigProperty(
				propertyName = "destination", propertyValue = "jms/BackEndWorkerListenTopic")
		}, 
		mappedName = "jms/as_MQLight-sampleservice")
public class BackEndMDB implements MessageListener {
	
	/** Consistent with WAR deployment, use a convention of our CF name being based on the bound service name */
	private static final String MQLIGHT_SERVICE_NAME = "MQLight-sampleservice";

	/** Topic object for publications obtained through resource injection */ 
	@Resource(name="jms/BackEndWorkerNotificationTopic", 
			lookup="jms/BackEndWorkerNotificationTopic", 
			type=Topic.class)
	Topic publishTopic;
	
	/** Connection factory for publications obtained through resource injection */ 
	@Resource(name="jms/BackendMDBConnectionFactory", 
			lookup="jms/" + MQLIGHT_SERVICE_NAME, 
			type=ConnectionFactory.class)
	ConnectionFactory mqlightCF;
	
    /** Simple logging */
	private final static Logger logger = Logger.getLogger(BackEndMDB.class.getName());	

    /** Generate an instance id */
	private final static long instanceId = System.currentTimeMillis();
	
    /**
     * Default constructor. 
     */
    public BackEndMDB() {
    }
	
	/**
	 * The MDB onMessage method is driven for each piece of data that arrives.
	 * 
	 * It is very important that you set
	 *  @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
	 * Liberty for Java does not support global (XA) transactions, and your
	 * onMessage method will almost certainly contain two transactional resources:
	 * 1) The Activation Specification that delivered you the message
	 * 2) The Connection Factory that you use to publish notifications
	 * So if you do not set your TransactionAttribute as NOT_SUPPORTED,
	 * the MDB will fail to process messages. 
	 * 
     * @see MessageListener#onMessage(Message)
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onMessage(Message message) {
        
    	
    	Connection jmsConn = null;
    	try {
    		// We only handle TextMessages
    		JSONObject inputData;
	    	if (message instanceof TextMessage) {
	    	  	TextMessage textMessage = (TextMessage)message;
	            logger.log(Level.INFO,"Received message" + textMessage.getText());
	    		inputData = JSONObject.parse(textMessage.getText());
	    	}
	    	else {
	    		throw new RuntimeException("Invalid message type: " + message.getJMSType());
	    	}
    		
	    	// *** This is our very simple processing logic for the sample ****
	    	String word = (String)inputData.get("word");
	    	if (word == null) throw new RuntimeException("Invalid data: " + inputData);
	    	String upperCaseWord = word.toUpperCase();
	    	logger.log(Level.INFO, "Processed word:" + word + " --> " + upperCaseWord);
	    	JSONObject notificationData = new JSONObject();
	    	notificationData.put("word", upperCaseWord);
	    	notificationData.put("backend", "Liberty:" + instanceId);
	    	// ***            Processing logic complete                    ****
	    	
	    	// Publish a notification that we have completed our work.
	    	// In our simple sample, this notification contains the upper case words
	    	// More regularly, the result of our work would have been stored somewhere
	    	// else (such as a database) and the MQ Light notification would just
	    	// contain an identifier for the work.
	    	
	    	// Connect to the service using the CF from our resource reference
	    	jmsConn = mqlightCF.createConnection();

	    	// Create a session.
	    	Session jmsSess = jmsConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	    	
	    	// Create a producer on our topic
	    	MessageProducer producer = jmsSess.createProducer(publishTopic);
	    	
	    	// Set an expiry on our messages
	    	producer.setTimeToLive(60*1000);
	    	
	    	// Create our message
	    	TextMessage textMessage = jmsSess.createTextMessage(notificationData.serialize());
    		
    		// Send it
	    	logger.log(Level.INFO, "Sending message " + textMessage.getText());
	    	producer.send(textMessage);
	    	
	    	// Cleanup
	    	producer.close();
	    	jmsSess.close();
	    	
    	}
    	catch (Exception e) {
    		// Consider the correct exception handling for your application.
    		// In this sample, we simply log the exception and return to the
    		// container WITHOUT an exception. This means the message will
    		// have been consumed
    		logger.log(Level.SEVERE, "Exception processing message received from MQ Light", e);    		
    	}
    	finally {
    		// Ensure we cleanup our connection
    		try {
    			if (jmsConn != null) jmsConn.close();
    		}
    		catch (Exception e) {
        		logger.log(Level.SEVERE, "JMSException closing connection to MQ Light", e);
    		}    		
    	}
    }

}
