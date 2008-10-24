package scs.demos.logmonitor.servant;

import scs.demos.logmonitor.LogMonitor;
import scs.demos.logmonitor.LogMonitorHelper;
import scs.demos.logmonitor.LogMonitorPOA;
import scs.event_service.EventSink;
import scs.event_service.EventSinkHelper;

import org.omg.CORBA.Any;
import org.omg.CORBA.ORB;

import scs.core.ConnectionDescription;
import scs.core.IReceptacles;
import scs.core.IReceptaclesHelper;
import scs.core.InvalidName;

import java.io.*;

public class LogMonitorServant extends LogMonitorPOA {

	private int identifier;
	private int interval;
	private String logfile;
	private LogMonitorComponent logmonitor = null;
	private IReceptacles infoReceptacle = null;
	private ConnectionDescription conns[];
	private int logMonitorCount; 

	public  LogMonitorServant(LogMonitorComponent logmonitor){
		this.logmonitor = logmonitor;
	}

	public void setId(int identifier){
		System.out.println("Setando ID: " + identifier);
		this.identifier = identifier;
	}   

	public int getId(){
		return identifier;
	}
	
	public void setMonitorInterval(int interval){
		this.interval = interval;
	}   

	public int getMonitorInterval(){
		return interval;
	}

	public void setLogFile(String logfile){
		this.logfile = logfile;
	}

	public String getLogFile(){
		return this.logfile;
	}

	public void publishLog() {
		infoReceptacle = IReceptaclesHelper.narrow(logmonitor.getFacetByName("infoReceptacle"));

		String line = "";
		try {
			FileReader input = new FileReader(this.logfile);
			BufferedReader bufRead = new BufferedReader(input);
			
            int count = 0;	// Line number of count 
            
            line = bufRead.readLine();
            count++;
            /*
            while (line != null){
                System.out.println(count+": "+line);
                line = bufRead.readLine();
                count++;
            }
            */
            bufRead.close();
			
        }catch (ArrayIndexOutOfBoundsException e){
			System.out.println("Usage: java ReadFile filename\n");			
		}catch (IOException e){
            e.printStackTrace();
        }

		Any logMessage = ORB.init().create_any();
		logMessage.insert_string(line);

		try {
			conns = infoReceptacle.getConnections("LogMonitor");
		} catch (InvalidName e) {
			e.printStackTrace();
		}

		System.out.println("LogMonitor " + identifier + " publish log");

		for (int i = 0; i < conns.length; i++) {
			EventSink eventChannelFacet = EventSinkHelper.narrow( conns[i].objref );
			eventChannelFacet.push(logMessage);
		}
	}
}

