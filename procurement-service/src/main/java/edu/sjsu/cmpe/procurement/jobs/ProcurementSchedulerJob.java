package edu.sjsu.cmpe.procurement.jobs;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import de.spinscale.dropwizard.jobs.Job;
import de.spinscale.dropwizard.jobs.annotations.Every;
import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import edu.sjsu.cmpe.procurement.ProcurementService;
import edu.sjsu.cmpe.procurement.domain.BookDetails;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Destination;
import javax.jms.JMSException;

/**
 * This job will run at every 5 second.
 */
@Every("5s")
public class ProcurementSchedulerJob extends Job 
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void doJob() 
    {
    	try
    	{
    		postToPublisher(ProcurementService.queueName, ProcurementService.topicName);
    	}
    	catch(Exception e)			
    	{ System.out.println(e.getMessage()); }
    			
    	try
    	{
    		getFromPublisher();
    	}
    	catch(Exception e)
    	{ System.out.println(e.getMessage()); }
    
    }
    
    //GET from publisher using HTTP
    public void getFromPublisher()throws JMSException
	{
	
		//Connection for HTTP GET
		Client client_resp = Client.create();
	
		WebResource webRes = client_resp.resource("http://54.193.56.218:9000/orders/37783");
	
		ClientResponse resp = webRes.accept("application/json").get(ClientResponse.class);
	
		if (resp.getStatus() != 200) 
		{
		   throw new RuntimeException("Error in GET : HTTP error code : " + resp.getStatus());
		}
	    
		BookDetails book=resp.getEntity(BookDetails.class);
	
		// Depending on Category, different topics published
		String user = "admin";
		String password =  "password";
		String host =  "54.193.56.218";
		int port = Integer.parseInt("61613");
	
		//Session Management
		StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
		factory.setBrokerURI("tcp://" + host + ":" + port);
		Connection connection = factory.createConnection(user, password);
		connection.start();    	
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		System.out.println("Connection established successfully");
	
		//Publishing to different destinations based on Topic
		for(int i=0; i<book.getShipped_books().length; i++)
		{
			Destination destination = new StompJmsDestination("/topic/37783.book."+book.getShipped_books()[i].getCategory());
			MessageProducer producer = session.createProducer(destination);	
			producer.setDeliveryMode(DeliveryMode.PERSISTENT);
			String data = book.getShipped_books()[i].getIsbn()+":"+"\""
					+book.getShipped_books()[i].getTitle()+"\":\""
					+book.getShipped_books()[i].getCategory()+"\":\""
					+book.getShipped_books()[i].getCoverimage()+"\"";
			System.out.println("data:"+data);
			TextMessage msg = session.createTextMessage(data);
			msg.setLongProperty("id", System.currentTimeMillis());
			producer.send(msg);
		}
		connection.close();
	}

    //Posting to Publisher using HTTP POST
	public void postToPublisher(String qname, String tname) throws Exception
	{
		String queueName = qname;
		String user = "admin";
		String password = "password";
		String host = "54.193.56.218";
		int port = Integer.parseInt("61613");
		String queue = queueName;
	
		String destination = queueName;
	
		StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
		factory.setBrokerURI("tcp://" + host + ":" + port);
	
		Connection connection = factory.createConnection(user, password);
		connection.start();
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination dest = new StompJmsDestination(destination);
	
		MessageConsumer consumer = session.createConsumer(dest);
		System.out.println("System ready and messages awaited from " + queue + " ...");
		String first = "{\"id\":\"37783\",\"order_book_isbns\":[";
		String last = "]}";
		String content ="";
		long waittime = 5000; 
	
		while(true) 
		{
			Message msg = consumer.receive(waittime);
		    if( msg instanceof  TextMessage ) 
		    {
			    String body = ((TextMessage) msg).getText();
			    content = content + body.substring(body.indexOf(":") + 1);
			    content = content + "," ;    	      	    
			    System.out.println("Obtaining messages from " + queue + " : Message : " + body);
	   	    }
		    else if(msg==null)
		    {
		    	System.out.println("Quitting: Message is Null.");
		    	break;
		    }
		    else
		    {
		    	System.out.println("Error : Unexpected message type: "+ msg.getClass() );
		    }
		}
		connection.close();
		String input = null;
		if(content == null || content.isEmpty()) 
		{
			System.out.println("Content is empty");
		} 
		else 
		{
	    	input = first + content.substring(0,content.lastIndexOf(',')) + last; 

	    	final Client client = Client.create();
	    	WebResource webResource = client.resource("http://54.193.56.218:9000/orders");
	    	ClientResponse response = webResource.type("application/json").post(ClientResponse.class, input);

	    	String output = response.getEntity(String.class);
       	
		}
	}

}