/*
 * Copyright (C) 2017 maschio.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package jolie.logger;

/**
 *
 * @author maschio
 */
public class NormalLogger extends AbstractLogger {

    @Override
    protected void writeLog(LogMessage logMessage) {
        if ( logMessage.getLoggerLevel() == LoggerLevel.SEVERE){
          System.out.print("SEVERE:" +"\n");
        }
        if ( logMessage.getLoggerLevel() == LoggerLevel.WARNING){
          System.out.print("WARNING:" +"\n");        
        }
        if ( logMessage.getLoggerLevel() == LoggerLevel.INFO){
          System.out.print("INFO:" +"\n");  
        }
        if ( logMessage.getLoggerLevel() == LoggerLevel.FINE){
          System.out.print("FINE:" +"\n");              
        }
        if ( logMessage.getLoggerLevel() == LoggerLevel.TRACE){
          System.out.print("TRACE:" +"\n");  
        }
        System.out.print(logMessage.getData().strValue() + "\n");
         
    }
    
}
