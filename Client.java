import java.io.*;
import java.net.*;
import java.util.Scanner;

/**Class containing {@link Client#main(String[])} which creates and runs a client program
 * which is used to connect to the server program using the IP address and port number.*/
public class Client
{
	/**Socket used to communicate with the server */
	private Socket socket;

	/**Used to recieve input from the server */
	private BufferedReader textIn;

	/**Used to write to the server */
	private PrintWriter textOut;

	/**Creates an instance of client and runs {@link Client#runClient()} 
	 * @param args unused command line arguments */
    public static void main(String[] args)
    {
        Client myClient = new Client();
		myClient.runClient();
    }
	
	/**Calls the methods required to run the client program. {@link Client#connectToServer()} connects the
	 * client to the server, {@link Client#handleOutputs()} and {@link Client#handleInputs()} create threads that run concurrently.*/
	private void runClient()
	{
		connectToServer();
		handleOutputs();
		handleInputs();
	}
	
	/**Gets input for the IP and port number from the user 
	 * and attempts to connect to the server by creating a socket with this. */
	private void connectToServer()
	{
		boolean connected = false; //If a connect is successful this becomes true and the while loop ends
		while(connected == false)
		{
			try
			{
				System.out.print("Input the IP address of the server you would like to connect to: ");
				String serverAddress = System.console().readLine();
				System.out.println("Input the port number of the server you would like to connect to: ");
				int portNumber = Selector.selectOptionInt(1025,65535);

				//Initialising the socket then using it to initialise the IOStreams
				socket = new Socket(serverAddress, portNumber);
				textIn = new BufferedReader( new InputStreamReader( socket.getInputStream() ) );
				textOut = new PrintWriter(socket.getOutputStream(), true);

				connected = true;
			}
			catch(Exception e)
			{
				System.out.println("Sorry, I was unable to connect using the port and address inputted. Please try again.");
			}
			
		}
		
	}

	/**Creates and runs a thread which sends the client's inputs to the server 
	 * <br> Calls {@link Client#closeConnection()} if the client loses connection to the server*/
	private void handleOutputs()
	{
		Thread outputsThread = new Thread()
		{
			public void run()
			{
				while(true)
				{
					try
					{
						String messageToServer = System.console().readLine(); //Gets input from client
						
						textOut.println(messageToServer); //Sends input to sever
						textOut.flush();
						
					}
					//Catching nullPointer and IO exceptions caused by disconnect to server
					catch(Exception e)
					{
						closeConnection();
					}
					
				}
			}
		};
		outputsThread.start();
	}

	/**Creates and runs a thread which recieves output from the server and prints it for the client
	 * <br> Calls {@link Client#closeConnection()} if the client loses connection to the server*/
	private void handleInputs()
	{
		Thread inputsThread = new Thread()
		{
			public void run()
			{
				while(true)
				{
					try
					{
						String messageFromServer = textIn.readLine(); //Reads output from the server

						//To make sure the program is closed if the socket cannot be read from
						//(On some systems an exception wasnt always thrown if the server couldn't be connected to, this makes sure this happens)
						if(messageFromServer == null) throw new IOException();

						System.out.println(messageFromServer); //prints output for the client
						
					}

					//Catching nullPointer and IO exceptions caused by disconnect to server
					catch(Exception e)
					{
						closeConnection();
					}
				}
			}
		};
		inputsThread.start();
	
	}
	
	/**Closes the {@link Client#socket} then closes the program. This method
	 * is synchronized so that both threads running in client do not attempt 
	 * to close the server at the same time if there is a disconnect*/
	synchronized private void closeConnection()
	{
		try 
		{ 
			socket.close(); 
			System.out.println("Disconnected from server.");
			System.exit(0); //Close the program
		} 
		catch (IOException e) 
		{
			System.err.println("Exception when closing the socket"); e.printStackTrace();
			System.exit(0); //Close the program						
		}
	}

}



//The selector class is used by both the client and the server programs. Since the programs are separate, the class is included in both java files.
/**An instance of this class is used as a way of displaying options and getting user input from the command line.*/
class Selector
{
	/**Scanner object to be used by this class to get user input*/
	private static Scanner scanner = new Scanner(System.in);
	
	/**Asks the user to input a number between a lower and upper bound and returns their input.
	 * <br>Calls {@link java.util.Scanner#nextInt}
	 * @param lowerBound The lowest attribute number the user can select
	 * @param upperBound The highest attribute number the user can select
	 * @return int - The attribute number the user has selected */
	public static int selectOptionInt(int lowerBound, int upperBound)
	{
		if(upperBound-lowerBound < 1 || lowerBound<0 || upperBound<=lowerBound)
		{throw new IllegalArgumentException("The bounds are invalid");}
		int select = -1;
		//Loop will run until the user has made select equal to a valid number
		while(select < lowerBound || select > upperBound)
		{
			//Print all options if there are less than 4 options so its easier to read
			if(upperBound-lowerBound < 4)
			{
				System.out.print("Enter ");
				for(int n = lowerBound; n <= upperBound-2; n++)
				{
					System.out.print(n+", ");
				}
				System.out.print(upperBound-1+" or "+upperBound+": ");
			}
			//Print the range of options if there are less than 4 options so it doesn't print too much
			else
			{
				System.out.print("Enter a choice from "+lowerBound+"-"+upperBound+": ");
			}
			
			try {select = scanner.nextInt();}
			catch(Exception e){scanner.next();} //scanner.next() clears the scanner if the user's input causes an exception
			
		}
		return select;
		
	}
	
}