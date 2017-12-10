import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Scanner;

/**Class containing {@link Server#main(String[])} which creates and runs a messaging server 
 * which people using a client program can connect to using the IP address and port number.*/
public class Server
{
	/**To be used to create sockets for client sessions */
	private ServerSocket ss;

	/**Collection of clientSessions running on the server that can be accessed using the clientSession object.
	 * <br> Accesses to clientSessions must be synchronized to prevent clashes as different threads access it.*/
	private HashSet<ClientSession> clientSessions = new HashSet<ClientSession>();

	/**For remembering the start time of the server after the server is set up. */
	private long serverStartTime;
	
	/**Main method creates an new instance of the server class and runs it using {@link Server#runServer()}
     * @param args Unused command line arguments*/
    public static void main(String[] args)
    {
		Server myServer = new Server();
		myServer.runServer();
	}
	
	/**Runs the methods required to run the server.
     * <br> Calls {@link Server#setupServer()}
	 * <br> Calls {@link Server#handleServerCommands()} and {@link Server#createSessionsOnRequest()} which run concurrently.*/
	private void runServer()
	{
		try
		{
			setupServer();
			handleServerCommands();
			createSessionsOnRequest();
		}
		catch(Exception e)
		{
			System.err.println("There was an exception while running server. More information: "); e.printStackTrace();
			shutdown(); //Shuts down the server if an exception occurs due to a connection failure.
		}
		
    }
	
	/**Initialises the serverSocket with a port number inputted by the user so that it can be used to create sockets for client sessions.
     * <br> Also calls {@link System#currentTimeMillis()} to record the start time for the server
	 * <br> Calls {@link Server#handleServerCommands()} and {@link Server#createSessionsOnRequest()} which run concurrently.
	 * @throws Exception Caused by problems with the network or creating the server socket*/
	private void setupServer() throws Exception
	{
		System.out.println("What port number would you like to use for this server?");
		ss = new ServerSocket(Selector.selectOptionInt(1025,65535)); //User is allowed to select viable a port number
		
		serverStartTime = System.currentTimeMillis();
		
		//Writes the IP address of the system running the server
		System.out.println("The message server at "+ InetAddress.getLocalHost()+ " is now waiting for connections...");
		
		System.out.println("As the server admin, you can talk to the clients by typing things and pressing enter,");
		System.out.println("you can also use commands to control the server. Type /help for a list of commands.");
		
	}
	
	/**Creates and runs a new thread which will recieve input from the admin at the server side and perform the corresponding action
	 * <br> Calls a multitude of different methods depending on the admin's input, will process the command if it begins with / or
	 * {@link Server#broadcast(String)} the input if it doesn't. Calls {@link Server#shutdown()} if an exception occurs. */
	private void handleServerCommands()
	{
		//Creating a thread to get input from the server admin to control the server
		Thread serverCommandsThread = new Thread()
		{
			//implementing run method of thread
			public void run()
			{
				while(true)
				{
					try
					{
						String adminInput = System.console().readLine();
						
						if(!adminInput.equals("")) //don't bother with comparison if theres no input
						{
							if(adminInput.charAt(0) == '/' ) //If theres a / then decide on the command
							{
								if(adminInput.equals("/help"))
								{
									printAdminCommands();
								}
								else if(adminInput.startsWith("/whisper")) 
								{
									processWhisperAdminCommand(adminInput);
								}
								else if(adminInput.startsWith("/kick")) 
								{
									processKickAdminCommand(adminInput);
								}
								else if( adminInput.equals("/serverTime") )
								{
									System.out.println("The server has been up for "+getServerRunTime()+" seconds.");
								}
								else if(adminInput.startsWith("/clientTime")) 
								{
									processClientTimeAdminCommand(adminInput);
								}
								else if( adminInput.equals("/IP") ) 
								{
									System.out.println( getServerAddress() );
								}
								else if( adminInput.equals("/clients") ) 
								{
									System.out.println( getNumberOfClients() );
								}
								else if(adminInput.equals("/close")) 
								{
									shutdown();
								}
								else 
								{
									System.out.println("That is not a valid command, type /help for a list of commands.");
								}
							}
							else broadcast("ADMIN: " + adminInput);
						
						}
					}
					//Catching nullPointer and IO exceptions
					catch(Exception e)
					{
						System.err.println("There was an exception while processing input from the server admin. More information: "); e.printStackTrace();
						shutdown();
					}
					
				}
				
			}
			
		};
		serverCommandsThread.start(); //Start running the thread
	}

	/**Prints the commands the admin can type to the command line */
	private void printAdminCommands()
	{
		System.out.println(	"/help - get list of commands you can use \n" +
							"/whisper (name) (message) - send a message to one person only \n" +
							"/kick (name) - kicks a client out of the server \n" +
							"/serverTime - get how long the server has been running for \n" +
							"/clientTime (name) - get how long a client has been connected for \n" +
							"/IP - get the servers IP address \n" +
							"/clients - get the number of clients in the server \n" +
							"/close - shutdown the server");
	}

	/**processes the command string to get the name of the client to send the message to and the message strings.
	 * <br> If the command format is crrect, calls {@link Server#whisper(String,String,String)} using the 
	 * sender name "ADMIN" and the reciever and message strings.
	 * @param adminInput The full command inputted by the admin*/
	private void processWhisperAdminCommand(String adminInput)
	{
		try
		{
			String[] parts = adminInput.trim().split(" "); //Splits the command into the different words
			String reciever = parts[1]; //Second word is the reciever
			String message = parts[2]; //Third word is first word of message

			//adds remaining words to the first word of the message
			if(parts.length > 3)
			{
				for(int x = 3; x < parts.length; x++)
				{
					message = message.concat(" "+parts[x]);
				}
			}
			whisper("ADMIN", reciever, message);
		}
		//Catching out of bounds and conversion exceptions
		catch(Exception e) 
		{
			System.out.println("The format is incorrect. Please make sure your command is in the form /whisper (name) (message)");
		}
	}

	/**processes the command string to get the name of the client to send the message to and the message strings.
	 * <br> If the command format is crrect, calls {@link Server#whisper(String,String,String)} using the 
	 * sender name "ADMIN" and the reciever and message strings.
	 * @param adminInput The full command inputted by the admin*/
	private void processKickAdminCommand(String adminInput)
	{
		try
		{
			String clientToKick = adminInput.substring(6, adminInput.length()).trim();
			kickClient( clientToKick );
		}
		catch(StringIndexOutOfBoundsException e)
		{
			System.out.println("Please include the name of the client to kick after the kick command");
		}
	}

	/**Compares the name inputted with the name of every client on the server and calls
	 * {@link ClientSession#forceSocketClose()} on this session if the name matches to cause the client to disconnect due to an exception.
	 * @param clientToKick The name of the client to kick*/
	private void kickClient(String clientToKick)
	{
		//Since we are accessing clientSessions, this block must 
		//be synchronized because other concurrent processes access clientSessions too
		synchronized(clientSessions)
		{
			//Writing to every client by looping through every clientSession
			for(ClientSession session : clientSessions)
			{
				if(session.getClientName() == null) {} //Do nothing (move onto next x)
				else if( session.getClientName().equals(clientToKick) ) 
				{
					try
					{
						session.forceSocketClose();
					}
					catch(IOException e)
					{
						System.err.println("The socket could not be closed so the client cannot be kicked"); e.printStackTrace();
					}
					return;
				}
				
			}
			//Printed if the return is never called after the client is found.
			System.out.println("There is nobody in the server called \""+clientToKick+"\"."); 

		}

	}

	/**processes the command string to get the name of the client to get the time from, 
	 * it then finds the client and gets the time using {@link ClientSession#getClientRunTime()}.
	 * @param adminInput The full command inputted by the admin*/
	private void processClientTimeAdminCommand(String adminInput)
	{
		try
		{
			//Processing the command to get the name of the client
			String clientToCheck = adminInput.substring(12, adminInput.length()).trim();

			boolean found = false;
			synchronized(clientSessions)
			{
				//Writing to every client by looping through every clientSession
				for(ClientSession session : clientSessions)
				{
					if(session.getClientName() == null) {} //Do nothing (move onto next x)
					else if( session.getClientName().equals(clientToCheck) ) 
					{
						System.out.println("The client has been connected for "+session.getClientRunTime()+" seconds.");
						found = true;
						break; //Stop loop since the clients been found
					}
					
				}
				if(found == false) System.out.println("There is nobody in the server called \""+clientToCheck+"\".");
				
			}
		}
		//The occurs if the command is not written correctly
		catch(StringIndexOutOfBoundsException e)
		{
			System.out.println("Please include the name of the client after the command");
		}
	}

	/**Sends a private message from the sender to the reciever 
	 * @param sender The name of the client(or ADMIN) sending the message
	 * @param reciever The of name the client(or ADMIN) recieving the message
	 * @param message The message being sent*/
	private void whisper(String sender, String reciever, String message)
	{
		boolean sent = false; //will become true when sending is confirmed
		synchronized(clientSessions)
		{
			//Print message to command line if reciever is ADMIN
			if( reciever.equals("ADMIN") )
			{
				System.out.println(sender+" whispered to you: "+message);
				sent = true;
			}
			//print message to client with the correct name if reciever is not ADMIN
			else
			{
				//Writing to every client by looping through every clientSession
				for(ClientSession session : clientSessions)
				{
					if(session.getClientName() == null) {} //Do nothing (move onto next x)
					else if( session.getClientName().equals(reciever) )
					{
						session.writeToClient(sender+" whispered to you: "+message);
						sent = true;
					}
				}
			}

			//Confirm sending to sender if sending is successful
			if(sent == true)
			{
				//Print message to command line if sender is ADMIN
				if( sender.equals("ADMIN") )
				{
					System.out.println("You whispered to "+reciever+": "+message);
				}
				//print message to client with the correct name if sender is not ADMIN
				else
				{
					for(ClientSession session : clientSessions)
					{
						if(session.getClientName() == null) {} //Do nothing (move onto next x)
						else if( ("["+session.getClientName()+"]").equals(sender) )
						{
							session.writeToClient("You whispered to "+reciever+": "+message);
						}
					
					}
				}
			}
			//Else tell them the reciever does not exist
			else
			{
				//Print message to command line if sender is ADMIN
				if( sender.equals("ADMIN") )
				{
					System.out.println("There is nobody in the server called \""+reciever+"\".");
				}
				//print message to client with the correct name if sender is not ADMIN
				else
				{
					for(ClientSession session : clientSessions)
					{
						if(session.getClientName() == null) {} //Do nothing (move onto next x)
						else if( ("["+session.getClientName()+"]").equals(sender) )
						{
							session.writeToClient("There is nobody in the server called \""+reciever+"\".");
						}
					
					}
				}
			}
			
		}

	}

	/**Returns the time since the server was set up in seconds
	 * @return long - The time in seconds since the server was set up*/
	private long getServerRunTime()
	{
		return ( System.currentTimeMillis() - serverStartTime ) / 1000;
	}

	/**Returns a message telling the user what the server address is, or a message telling the user
	 * that address cannot be recieved if there is an error
	 * @return String - Message informing the user of the IP address*/
	private String getServerAddress()
	{
		String output = "The server address could not be retreived";
		try {output = "The server is at: "+InetAddress.getLocalHost();}
		catch(IOException e) {System.err.println("Problem getting the IP address."); e.printStackTrace();}
		return output;
	}

	/**Returns a message telling the user how many clients are in the server.
	 * @return String - Message informing the user of the ammount of clients in the server*/
	private String getNumberOfClients()
	{
		synchronized(clientSessions)
		{
			return "There are "+clientSessions.size()+" clients in the server.";
		}
	}
	
	/**This method runs unitl the server shuts down. It waits for clients to attempt to connect at the
	 * serverSocket then creates a socket by accepting the request, it then creates a clientSession 
	 * using the socket and starts the client session thread using {@link ClientSession#start()} 
	 * (which calls {@link ClientSession#run()}), and finally adds the clientSession to {@link Server#clientSessions}.*/
	private void createSessionsOnRequest()
	{
		Socket socket;
		while(true)
		{
			try
			{
				socket = ss.accept();
			}
			catch(IOException e)
			{
				continue; //Continue running the loop to create sessions if a client fails to connect
			}
			ClientSession session = new ClientSession(socket);
			session.start();
			synchronized(clientSessions)
			{
				clientSessions.add(session);
			}
			
		}
		
	}
	
	/**Closes the serverSocket then closes the program */
	private void shutdown()
	{
		try 
		{ 
			ss.close(); 
			System.out.println( "The server has shut down." );	
			System.exit(0); //Close the program
		} 
		catch (Exception e) 
		{
			System.err.println("Problem shutting down the server."); e.printStackTrace();
			System.exit(0);
		}
	}
	
	/**Outputs the message to every client connected to the server (and the admin) using {@link ClientSession#writeToClient(String)} 
	 * @param message The string to be outputted to ever client (and admin)*/
	private void broadcast(String message)
	{
		System.out.println(message); //Writing to server
			
		synchronized(clientSessions)
		{
			//Writing to every client by looping through every clientSession
			for(ClientSession session : clientSessions)
			{
				session.writeToClient(message);
			}
		}

	}



	/**Class extending thread which handles setting up the session and input 
	 * from clients to the server. Runs concurrently to other clientSessions. */
	private class ClientSession extends Thread
	{
		/**The socket that the client has connected to. Used to get input from and to write to the client. */
		private Socket socket;

		/**Name of the client - used an an identifier */
		private String clientName;

		/**Used to read input from the client */
		private BufferedReader textIn;

		/**Used to write to the client */
		private PrintWriter textOut;

		/**The time the clientSession was created */
		private long clientStartTime;
	
		/**Initialises {@link ClientSession#socket} with the socket passed as a parameter
		 * @param socket Socket to be used to create IO streams between the session and the session*/
		ClientSession(Socket socket) 
		{
			this.socket = socket;
		}
		
		/**Sets up the IOStreams ({@link ClientSession#createIOStreams()}), gets the user's name 
		 * ({@link ClientSession#getInputForClientName()}), and handles inputs ({@link ClientSession#handleClientInputs()}).
		 * If an exception occurs due to a disconnect {@link ClientSession#closeSession()} is called.*/
		public void run()
		{
			try
			{
			createIOStreams();
			getInputForClientName();
			handleClientInputs();
			}
			catch(Exception e)
			{
				System.err.println("A client has lost connection to the server. Closing the session...");
			}
			finally
			{
				closeSession();
			}
			
		}
		
		/**initialises {@link ClientSession#textIn} and {@link ClientSession#textOut}. 
		 * Also assigns the start time to {@link ClientSession#clientStartTime}
		 * @throws IOException Caused if an IO stream cannot be created using the socket*/
		private void createIOStreams() throws IOException
		{
			textIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			textOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
			System.out.println("A client has connected to the server.");
			writeToClient("You have connected to the server.");

			clientStartTime = System.currentTimeMillis();

		}

		/**Keeps requesting the client inputs a username until {@link ClientSession#clientName} is assigned a valid name
		 * that is not equal to any clientNames in {@link Server#clientSessions}
		 * @throws Exception Caused by IOExceptions or NullPointer exceptions due to disconnects*/
		private void getInputForClientName() throws Exception
		{
			String chosenName = null;

			while(clientName == null)
			{
				writeToClient("Please input a username:");
				chosenName = textIn.readLine().trim(); //gets name from client

				//To make sure the client session is closed if the socket cannot be read from
				//(On some systems an exception wasnt always thrown if the connecting program shut down, this makes sure this happens)
				if(chosenName == null) throw new IOException(); 

				//checking the name is valid
				if( chosenName.equals("") ) 
				{
					writeToClient("You cannot have no name. Please input a valid name and press enter.");
					continue;
				}
				else if(chosenName.length() > 15)
				{
					writeToClient("Please keep your name less than 15 characters.");
					continue;
				}
				else if(chosenName.equals("ADMIN"))
				{
					writeToClient("Trying to be smart, eh? No, you cannot use that name.");
					continue;
				}

				//Compares the chosenName with the current clientNames in the server
				boolean alreadyTaken = false;
				synchronized(clientSessions)
				{
					for(ClientSession session : clientSessions)
					{
						if(session.getClientName() == null) {} //Do nothing (move onto next x)
						else if(session.getClientName().equals(chosenName)) alreadyTaken = true;
					}

				}

				if(alreadyTaken)
				{
					writeToClient("Sorry that name is already taken.");
				}
				else
				{
					clientName = chosenName;
					broadcast(clientName + " has joined the server.");
					writeToClient("Welcome to the server. You can type /help for a list of commands.");
				}

			}
			
		}

		/**Gets input from the client and performs the corresponding action
		  * <br> Calls a multitude of different methods depending on the admin's input, will process the 
		  * command if it begins with / or {@link Server#broadcast(String)} the input if it doesn't.
		  * @throws Exception IOExceptions or Nullpointer exceptions caused by disconnects */
		private void handleClientInputs() throws Exception
		{
			String line = null;
			while(true)
			{
				line = textIn.readLine();

				//To make sure the client session is closed if the socket cannot be read from
				//(On some systems an exception wasnt always thrown if the connecting program shut down, this makes sure this happens)
				if(line == null) throw new IOException();

				if(!line.equals("")) //will not process the input if the input is empty
				{
					if(line.charAt(0) == '/' )
					{
						if(line.equals("/help"))
						{
							printClientCommands();
						}
						else if(line.startsWith("/whisper")) 
						{
							processWhisperClientCommand(line);
						}
						else if(line.equals("/serverTime")) 
						{
							writeToClient("The server has been up for "+getServerRunTime()+" seconds.");
						}
						else if(line.equals("/connectedTime"))
						{
							writeToClient("You have been connected for "+getClientRunTime()+" seconds.");
						}
						else if(line.equals("/IP"))
						{
							writeToClient( getServerAddress() );
						}
						else if(line.equals("/clients"))
						{
							writeToClient( getNumberOfClients() );
						}
						else if(line.equals("/quit"))
						{
							return;
						}
						else 
						{
							writeToClient("That is not a valid command, type /help for a list of commands.");
						}
					}
					else broadcast("["+clientName+"]: " + line);
				}
				
			} //end of while loop

		}

		/**Outputs the commands the client can use to the client */
		private void printClientCommands()
		{
			writeToClient(	"/help - get list of commands you can use \n" +
							"/whisper (name) (message) - send a message to one person only \n" +
							"/serverTime - get how long the server has been running for \n" +
							"/connectedTime - get how long you have been connected for \n" +
							"/IP - get the servers IP address \n" +
							"/clients - get the number of clients in the server \n" +
							"/quit - leave the server");
		}

		/**processes the command string to get the name of the client to send the message to and the message strings.
		 * <br> If the command format is crrect, calls {@link Server#whisper(String,String,String)} using the 
	 	 * client's name and the reciever and message strings.
		 * @param line The full command inputted by the admin*/
		private void processWhisperClientCommand(String line)
		{
			try
			{
				String[] parts = line.trim().split(" "); //splits the commnd into indivual words
				String reciever = parts[1]; //gets name of client to send to
				String message = parts[2]; //gets first word of message
				//adds other words to the first word to form the message
				if(parts.length > 3)
				{
					for(int x = 3; x < parts.length; x++)
					{
						message = message.concat(" "+parts[x]);
					}
				}
				whisper("["+clientName+"]", reciever, message);
			}
			//Catching out of bounds and conversion exceptions
			catch(Exception e) 
			{
				writeToClient("The format is incorrect. Please make sure your command is in the form /whisper (name) (message)");
			}
		}

		/**Sends a message to the client using the output stream {@link ClientSession#textOut} 
		 * @param message The string to print to the client*/
		public void writeToClient(String message)
		{
			textOut.println(message); 
			textOut.flush();
		}

		/**Returns the name of the client
		 * @return String - The name of the client linked to this clientSession*/
		public String getClientName()
		{
			return clientName;
		}

		/**Returns the time the client has ben connected to the server for in seconds 
		 * @return long - The time the client has been connected for in seconds*/
		public long getClientRunTime()
		{
			return ( System.currentTimeMillis() - clientStartTime ) / 1000;
		}

		/**Forces the socket for this client session to close, to be used if you want to force the client to disconnect
		 * @throws IOException Caused by the socket being unable to close*/
		public void forceSocketClose() throws IOException
		{
			socket.close();
		}
		
		/**CLoses the socket for the session if its not closed already and removes the session from {@link Server#clientSessions}*/
		public void closeSession() 
		{
			if(clientName == null)
			{
				System.out.println("A client has disconnected from the server before entering their username.");
			}
			else
			{
				broadcast(clientName+" has left the server.");
			}
			
			try 
			{
				if(!socket.isClosed()) socket.close();
			}
			catch(IOException e)
			{
				System.err.println("There was an exception when closing a socket for a clientSession."); e.printStackTrace();
			}
			finally
			{
				clientSessions.remove(this);
			}

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