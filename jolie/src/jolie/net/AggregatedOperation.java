/***************************************************************************
 *   Copyright (C) 2009-2011 by Fabrizio Montesi <famontesi@gmail.com>     *
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
import java.net.URISyntaxException;
import jolie.ExecutionThread;
import jolie.Interpreter;
import jolie.SessionListener;
import jolie.SessionThread;
import jolie.State;
import jolie.lang.Constants;
import jolie.lang.Constants.OperationType;
import jolie.net.ports.OutputPort;
import jolie.process.OneWayProcess;
import jolie.process.Process;
import jolie.process.RequestResponseProcess;
import jolie.process.SequentialProcess;
import jolie.runtime.FaultException;
import jolie.runtime.OneWayOperation;
import jolie.runtime.RequestResponseOperation;
import jolie.runtime.VariablePath;
import jolie.runtime.typing.OperationTypeDescription;
import jolie.runtime.typing.TypeCheckingException;

/**
 * An AggregatedOperation instance contains information about an operation that is aggregated by an input port.
 * @author Fabrizio Montesi
 */
public abstract class AggregatedOperation
{
	private static class CourierOneWayAggregatedOperation extends AggregatedOperation {
		private final OneWayOperation operation;
		private final Process courierProcess;
		private final VariablePath inputVariablePath;
		
		public CourierOneWayAggregatedOperation(
			OneWayOperation operation,
			VariablePath inputVariablePath,
			Process courierProcess
		) {
			super( operation.id() );
			this.operation = operation;
			this.inputVariablePath = inputVariablePath;
			this.courierProcess = courierProcess;
		}
		
		public OperationType type()
		{
			return OperationType.ONE_WAY;
		}
		
		public void runAggregationBehaviour( final CommMessage requestMessage, final CommChannel channel )
			throws IOException, URISyntaxException
		{
			Interpreter interpreter = Interpreter.getInstance();
			try {
				operation.requestType().check( requestMessage.value() );

				ExecutionThread initThread = Interpreter.getInstance().initThread();
				try {
					initThread.join();
				} catch( InterruptedException e ) {
					throw new IOException( e );
				}

				State state = initThread.state().clone();
				Process p = new SequentialProcess( new Process[] {
					new OneWayProcess( operation, inputVariablePath ).receiveMessage( new SessionMessage( requestMessage, channel ), state ),
					courierProcess
				});
				SessionThread t = new SessionThread( p, state, initThread );
				
				final FaultException[] f = new FaultException[1];
				f[0] = null;
				t.addSessionListener( new SessionListener() {
					public void onSessionExecuted( SessionThread session )
					{}

					public void onSessionError( SessionThread session, FaultException fault )
					{
						// We need to send the acknowledgement
						if ( fault.faultName().equals( "CorrelationError" )
							|| fault.faultName().equals( "IOException" )
							|| fault.faultName().equals( "TypeMismatch" )
							) {
							synchronized( f ) {
								f[0] = fault;
							}
						} else {
							Interpreter.getInstance().logSevere( "Courier session for operation " + operation.id() + " has thrown fault " + fault.faultName() + ", which cannot be forwarded to the caller. Forwarding IOException." );
							synchronized( f ) {
								f[0] = new FaultException( jolie.lang.Constants.IO_EXCEPTION_FAULT_NAME, fault.faultName() );
							}
						}
					}
				} );
				t.start();
				try {
					t.join();
				} catch( InterruptedException e ) {}
				
				synchronized( f ) {
					if ( f[0] == null ) {
						// We need to send the acknowledgement
						channel.send( CommMessage.createEmptyResponse( requestMessage ) );
					} else {
						channel.send( CommMessage.createFaultResponse( requestMessage, f[0] ) );
					}
				}
			} catch( TypeCheckingException e ) {
				interpreter.logWarning( "TypeMismatch for received message (input operation " + operation.id() + "): " + e.getMessage() );
				try {
					channel.send( CommMessage.createFaultResponse( requestMessage, new FaultException( jolie.lang.Constants.TYPE_MISMATCH_FAULT_NAME, e.getMessage() ) ) );
				} catch( IOException ioe ) {
					Interpreter.getInstance().logSevere( ioe );
				}
			} finally {
				channel.disposeForInput();
			}
		}
		
		public OperationTypeDescription getOperationTypeDescription()
		{
			return operation.getOneWayTypeDescription();
		}
	}
	
	private static class CourierRequestResponseAggregatedOperation extends AggregatedOperation {
		private final RequestResponseOperation operation;
		private final Process courierProcess;
		private final VariablePath inputVariablePath;
		private final VariablePath outputVariablePath;
		
		public CourierRequestResponseAggregatedOperation(
			RequestResponseOperation operation,
			VariablePath inputVariablePath,
			VariablePath outputVariablePath,
			Process courierProcess
		) {
			super( operation.id() );
			this.operation = operation;
			this.inputVariablePath = inputVariablePath;
			this.outputVariablePath = outputVariablePath;
			this.courierProcess = courierProcess;
		}
		
		public OperationType type()
		{
			return OperationType.REQUEST_RESPONSE;
		}
		
		public void runAggregationBehaviour( final CommMessage requestMessage, final CommChannel channel )
			throws IOException, URISyntaxException
		{
			Interpreter interpreter = Interpreter.getInstance();
			try {
				operation.requestType().check( requestMessage.value() );

				ExecutionThread initThread = Interpreter.getInstance().initThread();
				try {
					initThread.join();
				} catch( InterruptedException e ) {
					throw new IOException( e );
				}

				State state = initThread.state().clone();
				Process p = new RequestResponseProcess( operation, inputVariablePath, outputVariablePath, courierProcess )
								.receiveMessage( new SessionMessage( requestMessage, channel ), state );
				new SessionThread( p, state, initThread ).start();
			} catch( TypeCheckingException e ) {
				interpreter.logWarning( "Received message TypeMismatch (input operation " + operation.id() + "): " + e.getMessage() );
				try {
					channel.send( CommMessage.createFaultResponse( requestMessage, new FaultException( jolie.lang.Constants.TYPE_MISMATCH_FAULT_NAME, e.getMessage() ) ) );
				} catch( IOException ioe ) {
					Interpreter.getInstance().logSevere( ioe );
				}
			} finally {
				channel.disposeForInput();
			}
		}

		public OperationTypeDescription getOperationTypeDescription()
		{
			return operation.typeDescription();
		}
	}
	
	private static class DirectAggregatedOperation extends AggregatedOperation {
		private final OutputPort outputPort;
		private final Constants.OperationType type;
		private final String name;
		
		public DirectAggregatedOperation( String name, Constants.OperationType type, OutputPort outputPort )
		{
			super( name );
			this.type = type;
			this.outputPort = outputPort;
			this.name = name;
		}
		
		public OperationType type()
		{
			return type;
		}
		
		public void runAggregationBehaviour( CommMessage requestMessage, CommChannel channel )
			throws IOException, URISyntaxException
		{
			CommChannel oChannel = outputPort.getNewCommChannel();
			oChannel.setRedirectionChannel( channel );
			oChannel.setRedirectionMessageId( requestMessage.id() );
			try {
				oChannel.setToBeClosed( true );
				oChannel.send( outputPort.getResourceUpdatedMessage( requestMessage ) );
			} catch( IOException e ) {
				channel.send( CommMessage.createFaultResponse( requestMessage, new FaultException( e ) ) );
			} finally {
				try {
					oChannel.setToBeClosed( false );
					oChannel.disposeForInput();
				} finally {
					channel.disposeForInput();
				}
			}
		}

		public OperationTypeDescription getOperationTypeDescription()
		{
			if ( type == OperationType.ONE_WAY ) {
				return outputPort.getInterface().oneWayOperations().get( name );
			} else {
				return outputPort.getInterface().requestResponseOperations().get( name );
			}
		}
	}

	private final String name;
	
	private AggregatedOperation( String name )
	{
		this.name = name;
	}
	
	public static AggregatedOperation createDirect( String name, Constants.OperationType type, OutputPort outputPort )
	{
		return new DirectAggregatedOperation( name, type, outputPort );
	}
	
	public static AggregatedOperation createWithCourier(
		OneWayOperation operation,
		VariablePath inputVariablePath,
		Process courierProcess
	) {
		return new CourierOneWayAggregatedOperation( operation, inputVariablePath, courierProcess );
	}
	
	public static AggregatedOperation createWithCourier(
		RequestResponseOperation operation,
		VariablePath inputVariablePath,
		VariablePath outputVariablePath,
		Process courierProcess
	) {
		return new CourierRequestResponseAggregatedOperation( operation, inputVariablePath, outputVariablePath, courierProcess );
	}
	
	/**
	 * Returns the operation type of this operation
	 * @return the operation type of this operation
	 * @see OperationType
	 */
	public abstract OperationType type();

	public abstract OperationTypeDescription getOperationTypeDescription();

	/**
	 * Returns the name of this operation.
	 * @return the name of this operation.
	 */
	public String name()
	{
		return name;
	}
        
	public abstract void runAggregationBehaviour( CommMessage requestMessage, CommChannel channel )
		throws IOException, URISyntaxException;
}
