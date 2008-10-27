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
		
		try {		
			//Retrieving LogViewer Facet
			LogViewer viewer = LogViewerHelper.narrow(logviewer.getFacet("scs::demos::logmonitor::LogViewer"));
		
			// Converging log messages
			String msg = event.extract_string();
			BufferedWriter out = new BufferedWriter(new FileWriter(viewer.getLogFile(), true));
			System.out.println(msg);
			out.write(msg);
			out.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void disconnect() {
		System.out.println("Foi Desconectado");
	}
}

