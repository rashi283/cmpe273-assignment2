package edu.sjsu.cmpe.library;

import java.net.URL;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.assets.AssetsBundle;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.views.ViewBundle;

import edu.sjsu.cmpe.library.api.resources.BookResource;
import edu.sjsu.cmpe.library.api.resources.RootResource;
import edu.sjsu.cmpe.library.config.LibraryServiceConfiguration;
import edu.sjsu.cmpe.library.domain.Book;
import edu.sjsu.cmpe.library.repository.BookRepository;
import edu.sjsu.cmpe.library.repository.BookRepositoryInterface;
import edu.sjsu.cmpe.library.ui.resources.HomeResource;

public class LibraryService extends Service<LibraryServiceConfiguration> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static String queueName;
    private static String topicName;
    private static String username;
    private static String password;
    private static String hostName;
    private static String portName;
    
    public static BookRepositoryInterface bookRepository;
    
    public static void main(String[] args) throws Exception 
    {
    	new LibraryService().run(args);
    	int numThreads = 1;
    	ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    
	    Runnable backgroundTask = new Runnable() 
	    {	 
		    @Override
		    public void run() 
		    {
		    	try
		    	{
			    	String user =  LibraryService.username;	//"admin";
			    	String password =  LibraryService.password;	//"password";
			    	String host =  LibraryService.hostName; //"54.193.56.218" - Host Y;
			    	int port = Integer.parseInt(LibraryService.portName);
			    	String destination = topicName;
		
			    	StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
			    	factory.setBrokerURI("tcp://" + host + ":" + port);
		
			    	Connection connection = factory.createConnection(user, password);
			    	connection.start();
		
			    	Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		
			    	Destination dest = new StompJmsDestination(destination);        
		
			    	MessageConsumer consumer = session.createConsumer(dest);	    	
		
			    	System.currentTimeMillis();
			    	System.out.println("System ready and awaiting messages...");
		
			    	while(true) 
			    	{
			    	    Message msg = consumer.receive();	    	    
			    	    if( msg instanceof  TextMessage ) {
			    		String body = ((TextMessage) msg).getText();
		
			    		if( "SHUTDOWN".equals(body)) 
			    		{
			    		    break;
			    		}
			    		System.out.println("Received message = " + body);
		
		
			    	    String[] token = body.split("\"");
			    	    Long isbn = Long.parseLong(token[0].replaceAll(":",""));
			    	    Book book =  bookRepository.getBookByISBN(isbn);	    	     	    
		
			    	    if(book==null){
			    	    	System.out.println("Book not present in the Hashmap");
			    	    	Book newBook= new Book();
			    	    	newBook.setIsbn(isbn);
			    	    	newBook.setCategory(token[3]);	    	    	
			    	    	newBook.setCoverimage(new URL(token[5]));	    	
			    	    	newBook.setStatus("available");
			    	    	newBook.setTitle(token[1]);
			    	        bookRepository.addBook(newBook);	
			    	        System.out.println("New Book Added::"+newBook.getTitle());
		
			    	    }
			    	    else if(book.getStatus().contains("lost"))
			    	    {
			    	    	System.out.println("Book :"+isbn+" Status::"+ book.getStatus());
			    	    	book.setStatus("available");
			    	    	bookRepository.updateBook(isbn, book);
			    	    	System.out.println("Status updated");
			    	    }
		
			    	    } 
			    	}
			    	//Close the connection after checking book status and adding it to Queue.
			    	connection.close();
		    	}
		    	catch(Exception e){System.out.println("Exception::"+e.getMessage());}
		    }
	
		};	
		executor.execute(backgroundTask);
		System.out.println("Successfully submitted lost Book isbn to Queue");
		executor.shutdown();
    }

    @Override
    public void initialize(Bootstrap<LibraryServiceConfiguration> bootstrap) 
    {
		bootstrap.setName("library-service");
		bootstrap.addBundle(new ViewBundle());
		bootstrap.addBundle(new AssetsBundle());
    }

    @Override
    public void run(LibraryServiceConfiguration configuration, Environment environment) throws Exception 
    {
		// This is how you pull the configurations from library_x_config.yml
		String queueName = configuration.getStompQueueName();
		String topicName = configuration.getStompTopicName();
		log.debug("{} - Queue name is {}. Topic name is {}",configuration.getLibraryName(), queueName,topicName);
		// TODO: Apollo STOMP Broker URL and login
	
		/** Root API */
		environment.addResource(RootResource.class);
		/** Books APIs */
		BookRepositoryInterface bookRepository = new BookRepository();
		environment.addResource(new BookResource(bookRepository));
	
		/** UI Resources */
		environment.addResource(new HomeResource(bookRepository));
    }

	public static String getQueueName() {
		return queueName;
	}

	public static void setQueueName(String queueVal) {
		LibraryService.queueName = queueVal;
	}

	public static String getTopicName() {
		return topicName;
	}

	public static void setTopicName(String topicVal) {
		LibraryService.topicName = topicVal;
	}

	public static String getUsername() {
		return username;
	}

	public static void setUsername(String usernameVal) {
		LibraryService.username = usernameVal;
	}

	public static String getPassword() {
		return password;
	}

	public static void setPassword(String passwordVal) {
		LibraryService.password = passwordVal;
	}

	public static String getHostName() {
		return hostName;
	}

	public static void setHostName(String hostVal) {
		LibraryService.hostName = hostVal;
	}

	public static String getPortName() {
		return portName;
	}

	public static void setPortName(String portVal) {
		LibraryService.portName = portVal;
	}   
}