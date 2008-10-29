package scs.demos.logmonitor.servant;

import org.omg.CORBA.ORB;
import org.omg.CORBA.Object;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.Any;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import scs.container.ComponentAlreadyLoaded;
import scs.container.ComponentCollection;
import scs.container.ComponentCollectionHelper;
import scs.container.ComponentHandle;
import scs.core.ComponentId;
import scs.container.ComponentLoader;
import scs.container.ComponentLoaderHelper;
import scs.container.ComponentNotFound;
import scs.container.LoadFailure;
import scs.event_service.ChannelCollection;
import scs.event_service.ChannelCollectionHelper;
import scs.event_service.ChannelDescr;
import scs.event_service.ChannelFactory;
import scs.event_service.ChannelFactoryHelper;
import scs.event_service.ChannelManagement;
import scs.event_service.ChannelManagementHelper;
import scs.event_service.EventSink;
import scs.event_service.EventSinkHelper;
import scs.event_service.NameAlreadyInUse;
import scs.event_service.servant.ConnectionStatus;
import scs.event_service.servant.EventSinkConsumerServant;
import scs.core.AlreadyConnected;
import scs.core.ExceededConnectionLimit;
import scs.core.IComponent;
import scs.core.IComponentHelper;
import scs.core.IReceptacles;
import scs.core.IReceptaclesHelper;
import scs.core.IMetaInterface;
import scs.core.IMetaInterfaceHelper;
import scs.core.InvalidConnection;
import scs.core.InvalidName;
import scs.core.StartupFailed;
import scs.core.FacetDescription;
import scs.demos.logmonitor.LogViewer;
import scs.demos.logmonitor.LogViewerHelper;
import scs.demos.logmonitor.servant.EventSinkViewerServant;
import scs.execution_node.ContainerAlreadyExists;
import scs.execution_node.ExecutionNode;
import scs.execution_node.ExecutionNodeHelper;
import scs.execution_node.InvalidProperty;
import scs.execution_node.Property;

import java.io.*;

public class LogViewerApp {

	private static final String EXEC_NODE_NAME = "ExecutionNode";
	private static final String EXEC_NODE_FACET = "scs::execution_node::ExecutionNode";
	// CHANGED
	private static final String CONTAINER_NAME = "LogViewerContainer";
	private static final String EVCONTAINER_NAME = "EventChannelContainer";

	private ExecutionNode[] execNode = null;
	private IComponent logMonitorComp = null;
	private IComponent logViewerComp = null;
	private String exception;

	public LogViewerApp(String evHost, String evPort, String viewerHost, String viewerPort) {
		if (!initialize(evHost, evPort, viewerHost, viewerPort))
			System.err.println("Erro iniciando a aplicacao");
	}

	/**
		* Cria um container no ExecutionNode corrente
		*/
	private boolean createContainer(String name, ExecutionNode execNode) {
		try {
			Property prop = new Property();
			prop.name = "language";
			prop.value = "java";
			Property propSeq[] = { prop };
			IComponent container = execNode.startContainer(name, propSeq);

			if (container == null) {
				return false;
			}

		} catch (ContainerAlreadyExists e) {
			System.err.println("Ja existe um container com este nome.");
			return false;
		}
		catch (InvalidProperty e) {
			System.err.println("Propriedade inválida!");
			return false;
		}
		return true;
	}

	
	
	/* Cria um component no container associado a loader*/
	private ComponentHandle createHandle(ComponentLoader loader, ComponentId compId){
		ComponentHandle handle = null;

		try {
			handle = loader.load(compId, new String[] { "" });
			handle.cmp.startup();
		} catch (ComponentNotFound e) {
			System.out.println("WorkerInitializer::createHandle - Componente " + compId.name + " nao encontrado.");
		} catch (ComponentAlreadyLoaded e) {
			System.out.println("WorkerInitializer::createHandle - Componente " + compId.name + " já foi criado.");
		} catch (LoadFailure e) {
			System.out.println("WorkerInitializer::createHandle - Erro ao carregar componente " + compId.name + ".\n");
		} catch (StartupFailed e) {
			System.out.println("WorkerInitializer::createHandle - Startup do componente " + compId.name + " falhou.\n");
		}

		return handle;
	}

	/**
		* @param args
		*/
	private boolean initialize(String evHost, String evPort, String viewerHost, String viewerPort) {

		String evCorbaname = null;
		String viewerCorbaname = null;
		int id = 1;

		execNode = new ExecutionNode[2];
	
		String[] evArgs = new String[2];
		evArgs[0] = evHost;
		evArgs[1] = evPort;
		
		String[] viewerArgs = new String[2];
		viewerArgs[0] = viewerHost;
		viewerArgs[1] = viewerPort;


		

		evCorbaname = "corbaname::" + evHost + ":" + evPort + "#"	+ EXEC_NODE_NAME ;
		viewerCorbaname = "corbaname::" + viewerHost + ":" + viewerPort + "#"	+ EXEC_NODE_NAME ;

		System.out.println("Conectando ao execution node: " + evCorbaname);

		ORB orb = ORB.init(evArgs, null);

		//Connecting to LogMonitor/EventManager/EventChannel Execution Node
		try {
			org.omg.CORBA.Object obj = orb.string_to_object(evCorbaname);
			IComponent execNodeComp = IComponentHelper.narrow(obj);
			Object ob = execNodeComp.getFacet(EXEC_NODE_FACET);
			execNode[0] = ExecutionNodeHelper.narrow(ob);

		} catch (SystemException ex) {
			System.err.println("Erro ao conectar com o ExecutionNode " + evCorbaname);
			System.exit(1);
		}

		orb = ORB.init(viewerArgs, null);

		//Creating Log Viewer Execution Node
		try {
			org.omg.CORBA.Object obj = orb.string_to_object(viewerCorbaname);
			IComponent execNodeComp = IComponentHelper.narrow(obj);
			execNodeComp.startup();
			Object ob = execNodeComp.getFacet(EXEC_NODE_FACET);
			execNode[1] = ExecutionNodeHelper.narrow(ob);

		} catch (SystemException ex) {
			System.err.println("Erro ao conectar com o ExecutionNode " + viewerCorbaname);
			System.exit(1);
		} catch (StartupFailed e) {
			System.err.println("Startup do ExecutionNode " + viewerCorbaname + "falhou.");
			System.exit(1);
		}

		//Creating Log Viewer Container
		if (!this.createContainer(CONTAINER_NAME,execNode[1])) {
			System.err.println("Erro criando o container em " + viewerCorbaname);
			return false;
		}

		//Retrieving LogMonitor/EventChannel Container
		IComponent evContainer;
		evContainer = execNode[0].getContainer(EVCONTAINER_NAME);

		//Retrieving LogViewer Container
		IComponent viewerContainer;
		viewerContainer = execNode[1].getContainer(CONTAINER_NAME);

		//Starting Container
		try {
			viewerContainer.startup();
		} catch (StartupFailed e) {
			System.out.println("Erro no startup do container em " + viewerCorbaname);
			System.exit(1);
		}

		//Getting Component Collection Facet from container
		ComponentCollection compCollection = ComponentCollectionHelper.narrow(evContainer
			.getFacet("scs::container::ComponentCollection"));
		if (compCollection == null) {
			System.out.println("Erro ao retornar faceta loader em " + evCorbaname);
			return false;
		}

		//Getting Component Loader Interface
		ComponentLoader loader = ComponentLoaderHelper.narrow(viewerContainer
			.getFacet("scs::container::ComponentLoader"));
		if (loader == null) {
			System.out.println("Erro ao retornar faceta loader em " + viewerCorbaname);
			return false;
		}

		//Getting Event Manager Reference
		ComponentHandle eventMgrHandle = null;
		ComponentId eventMgrCompId = new ComponentId();
		eventMgrCompId.name = "EventManager";
		eventMgrCompId.version = 1;

		ComponentHandle [] handles = compCollection.getComponent(eventMgrCompId);
		eventMgrHandle = handles[0];
		if (eventMgrHandle == null) {
			return false;
		}

		IComponent eventMgr = eventMgrHandle.cmp;

		//Loading Log Viewer Component
		ComponentHandle logViewerHandle = null;
		ComponentId logViewerCompId = new ComponentId();
		logViewerCompId.name = "LogViewer";
		logViewerCompId.version = 1;


		logViewerHandle = createHandle(loader, logViewerCompId);
		if (logViewerHandle == null) {
			return false;
		}

		logViewerComp = logViewerHandle.cmp;

		//Getting EventSink Facet from LogViewer Component
		EventSink eventSinkViewer = EventSinkHelper.narrow(logViewerComp.getFacet("scs::demos::logmonitor::EventSink"));
		if( logViewerComp.getFacetByName("EventSink") == null ) {
			System.out.println("WorkerInitializer::buildChannel - Erro ao retornar eventSinkViewer !");
			return false;
		}

		//Getting Channel Collection Facet from Event Manager Component
		ChannelCollection chCollection = ChannelCollectionHelper.narrow(eventMgr.getFacet("scs::event_service::ChannelCollection"));
		if( chCollection == null ) {
			System.out.println("WorkerInitializer::buildChannel - Erro ao retornar ChannelCollection !");
			return false;
		}

		IComponent masterChannel = null;

		try {
			//Getting event channel
			masterChannel = chCollection.getChannel("MasterChannel");
			IReceptacles eventSource = IReceptaclesHelper.narrow(masterChannel.getFacet("scs::core::IReceptacles"));

			//Connecting log viewer with event channel
			try {
				eventSource.connect("EventSource", eventSinkViewer);
			} catch (Exception e) {
				System.out.println("WorkerInitializer::buildChannel - Erro ao conectar source no sink." + e.getMessage());
				return false;
			}

		} catch (Exception e) {
			System.out.println("WorkerInitializer::buildChannel - Erro ao instanciar channel.\n");
			return false;
		}

	
		return true;
	}

	public static void main(String[] args) {
		
		// Input parameters
		InputStreamReader isr = new InputStreamReader ( System.in );
		BufferedReader br = new BufferedReader ( isr );
		String opt = "";
		String evHost = "localhost";
		String evPort = "1050";
		String viewerHost = "localhost";
		String viewerPort = "1050";
		String logFilename = "output.log";
		
		try {
			
			System.out.print("Event Channel ExecutionNode Host (default is localhost): ");
			opt = br.readLine();
			if(!opt.equals("")) {
				evHost = opt;
			}
			opt = "";
			System.out.print("Event Channel ExecutionNode Port (default is 1050): ");
			opt = br.readLine();
			if(!opt.equals("")) {
				evPort = opt;
			}
			System.out.print("Log Viewer ExecutionNode Host (default is localhost): ");
			opt = br.readLine();
			if(!opt.equals("")) {
				viewerHost = opt;
			}
			opt = "";
			System.out.print("Log Viewer ExecutionNode Port (default is 1050): ");
			opt = br.readLine();
			if(!opt.equals("")) {
				viewerPort = opt;
			}
			opt = "";
			System.out.print("Output file complete path (default is ./output.log): ");
			opt = br.readLine();
			if(!opt.equals("")) {
				logFilename = opt;
			}
			
			long start = System.currentTimeMillis();
			
			LogViewerApp app = new LogViewerApp(evHost, evPort, viewerHost, viewerPort);
			
			//Run App
			app.run(logFilename);
			
			System.out.print("Press any key to stop monitoring ... ");
			opt = br.readLine();
			
			long end = System.currentTimeMillis();
			
			System.out.println("Tempo total de execucao:" + (end - start));
			
			app.stop();
		} catch (SystemException ex) {
			ex.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	public void run(String logfile) throws InterruptedException {
		//Retrieving LogViewer Facet
		LogViewer viewer = LogViewerHelper.narrow(logViewerComp.getFacet("scs::demos::logmonitor::LogViewer"));
		//Setting log file path, passed via arguments
		viewer.setLogFile(logfile);

	}

	public void stop() {
		try{
			execNode[1].stopContainer(CONTAINER_NAME); 
			Thread.sleep(1000);
		} catch (Exception e ) {
			System.err.println("Erro ao finalizar container");
		}
	}
}
