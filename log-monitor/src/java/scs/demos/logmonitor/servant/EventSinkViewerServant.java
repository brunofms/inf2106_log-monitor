package scs.demos.logmonitor.servant;

import scs.event_service.EventSinkPOA;
import scs.demos.logmonitor.LogViewer;
import scs.demos.logmonitor.LogViewerHelper;

import org.omg.CORBA.Any;
import java.io.*;

public class EventSinkViewerServant extends EventSinkPOA {

	private int identifier;
	private LogViewerComponent logviewer = null;
	private int logViewerCount; 

	public EventSinkViewerServant(LogViewerComponent logviewer){
		this.logviewer = logviewer;
	}

	public void push(Any event) {
		
		//Retrieving LogViewer Facet
		LogViewer viewer = LogViewerHelper.narrow(logviewer.getFacet("scs::demos::logmonitor::LogViewer"));
		//Getting log file path
		String logfile = viewer.getLogFile();
		
		PrintStream ps = null;
		try {
			ps = new PrintStream(new FileOutputStream(logfile),true);
		} catch (IOException e) {

		}	

		//ps.print(event.extract_string() + "\n");
		ps.close();
	}
	
	public void disconnect() {
		System.out.println("Foi Desconectado");
	}
}

