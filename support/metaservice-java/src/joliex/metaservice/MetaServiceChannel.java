/***************************************************************************
 *   Copyright (C) 2008 by Fabrizio Montesi                                *
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

package joliex.metaservice;

import java.io.IOException;
import jolie.net.CommChannel;
import jolie.net.CommMessage;
import jolie.runtime.FaultException;
import jolie.runtime.Value;

/**
 * A simple channel for exchanging messages with a service powered by MetaService.
 * @author Fabrizio Montesi
 */
public class MetaServiceChannel
{
	final private MetaService metaService;
	final private String resourceName;
	private CommChannel channel;
	final private boolean persistent;
	
	public MetaServiceChannel( MetaService metaService, String resourceName, boolean persistent )
		throws IOException
	{
		this.metaService = metaService;
		this.resourceName = "/" + resourceName;
		this.persistent = persistent;
		if ( persistent ) {
			channel = metaService.createCommChannel();
		}
	}
	
	public String resourceName()
	{
		return resourceName;
	}
	
	public void send( String operationName, Value value )
		throws IOException
	{
		if ( !persistent ) {
			channel = metaService.createCommChannel();
		}
		channel.send(
			new CommMessage( operationName, resourceName, value )
		);
	}
	
	public Value recv()
		throws IOException, FaultException
	{
		CommMessage message = channel.recv();
		if ( message.isFault() ) {
			throw message.fault();
		}
		return message.value();
	}
}