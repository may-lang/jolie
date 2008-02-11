/***************************************************************************
 *   Copyright (C) by Fabrizio Montesi                                     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/


package jolie.net;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

import jolie.Constants;
import jolie.Interpreter;
import jolie.deploy.InputPort;
import jolie.runtime.InputOperation;
import jolie.runtime.InvalidIdException;

/** Handles the networking communications.
 * The CommCore class represent the communication core of JOLIE.
 */
public class CommCore
{
	private Vector< CommListener > listeners = new Vector< CommListener >();
	private HashMap< URI, Collection< InputPort > > serviceMap = new HashMap< URI, Collection< InputPort > >();
	
	private ThreadGroup threadGroup;
	
	private Logger logger = Logger.getLogger( "JOLIE" );
	
	private int connectionsLimit = -1;
	private Interpreter interpreter;

	public CommCore( Interpreter interpreter, int connectionsLimit )
	{
		this.interpreter = interpreter;
		this.connectionsLimit = connectionsLimit;
		this.threadGroup = new ThreadGroup( "CommCore-" + interpreter.hashCode() );
	}
	
	public Logger logger()
	{
		return logger;
	}
	
	public int connectionsLimit()
	{
		return connectionsLimit;
	}

	public ThreadGroup threadGroup()
	{
		return threadGroup;
	}
	
	/** Adds an input service.
	 * @todo Implement different mediums than socket.
	 * @param uri
	 * @param protocol
	 */
	public void addService( URI uri, CommProtocol protocol, Collection< InputPort > inputPorts )
		throws UnsupportedCommMediumException, IOException
	{
		String medium = uri.getScheme();
		CommListener listener = null;
		if ( Constants.stringToMediumId( medium ) == Constants.MediumId.SOCKET ) {
			listener = new SocketListener( interpreter, protocol, uri.getPort(), inputPorts );
		} else
			throw new UnsupportedCommMediumException( medium );
		
		assert listener != null;
		listeners.add( listener );
		serviceMap.put( uri, inputPorts );
	}
	
	public HashMap< URI, Collection< InputPort > > serviceMap()
	{
		return serviceMap;
	}
	
	private ExecutorService executorService;
	
	private class CommThreadFactory implements ThreadFactory {
		public Thread newThread( Runnable r )
		{
			return new CommChannelHandler( r );
		}
	}

	private class CommChannelHandlerRunnable implements Runnable {
		private CommChannel channel;
		private CommListener listener;
		
		public CommChannelHandlerRunnable( CommChannel channel, CommListener listener )
		{
			this.channel = channel;
			this.listener = listener;
		}
		
		public void run()
		{
			try {
				CommMessage message = channel.recv();
				InputOperation operation =
						interpreter.getInputOperation( message.inputId() );
				if ( listener != null && !listener.canHandleInputOperation( operation ) )
					Interpreter.getInstance().logger().warning(
								"Discarded a message for operation " + operation +
								", not specified in an input port at the receiving service."
							);
				else
					operation.recvMessage( channel, message );
			} catch( IOException ioe ) {
				ioe.printStackTrace();
			} catch( InvalidIdException iie ) {
				iie.printStackTrace();
			}
		}
	}
	
	public void scheduleReceive( CommChannel channel, CommListener listener )
	{
		executorService.execute( new CommChannelHandlerRunnable( channel, listener ) );
	}
	
	/** Initializes the communication core. */
	public void init()
	{
		if ( connectionsLimit > 0 )
			executorService = Executors.newFixedThreadPool( connectionsLimit, new CommThreadFactory() );
		else
			executorService = Executors.newCachedThreadPool( new CommThreadFactory() );
		for( CommListener listener : listeners )
			listener.start();
	}

	/** Shutdowns the communication core, interrupting every communication-related thread. */
	public void shutdown()
	{
		threadGroup.interrupt();
	}
}
