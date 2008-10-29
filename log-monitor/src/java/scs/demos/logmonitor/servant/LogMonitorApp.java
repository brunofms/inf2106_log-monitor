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
import scs.demos.logmonitor.LogMonitor;
import scs.demos.logmonitor.LogMonitorHelper;
import scs.execution_node.ContainerAlreadyExists;
import scs.execution_node.ExecutionNode;
import scs.execution_node.ExecutionNodeHelper;
import scs.execution_node.InvalidProperty;
import scs.execution_node.Property;

import java.io.*;

public class LogMonitorApp {

	private static final String EXEC_NODE_NAME = "ExecutionNode";
	private static final String EXEC_NODE_FACET = "scs::execution_node::ExecutionNode";
	// CHANGED
	private static final String CONTAINER_NAME = "LogMonitorContainer";
	private static final String EVCONTAINER_NAME = "EventChannelContainer";

	private ExecutionNode[] execNode = null;
	private IComponent logMonitorComp = null;
	private String exception;

	public LogMonitorApp(String evHost, String evPort, String monitorHost, String monitorPort) {
		if (!initialize(evHost, evPort, monitorHost, monitorPort))
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
	private boolean initialize(String evHost, String evPort, String monitorHost, String monitorPort) {

		String evCorbaname = null;
		String monitorCorbaname = null;
		int id = 1;

		execNode = new ExecutionNode[2];
	
		String[] evArgs = new String[2];
		evArgs[0] = evHost;
		evArgs[1] = evPort;
		
		String[] monitorArgs = new String[2];
		monitorArgs[0] = monitorHost;
		monitorArgs[1] = monitorPort;


		

		evCorbaname = "corbaname::" + evHost + ":" + evPort + "#"	+ EXEC_NODE_NAME ;
		monitorCorbaname = "corbaname::" + monitorHost + ":" + monitorPort + "#"	+ EXEC_NODE_NAME ;

		System.out.println("Conectando ao execution node: " + evCorbaname);

		ORB orb = ORB.init(evArgs, null);

		//Connecting to EventManager/EventChannel Execution Node
		try {
			org.omg.CORBA.Object obj = orb.string_to_object(evCorbaname);
			IComponent execNodeComp = IComponentHelper.narrow(obj);
			Object ob = execNodeComp.getFacet(EXEC_NODE_FACET);
			execNode[0] = ExecutionNodeHelper.narrow(ob);

		} catch (SystemException ex) {
			System.err.println("Erro ao conectar com o ExecutionNode " + evCorbaname);
			System.exit(1);
		}

		orb = ORB.init(monitorArgs, null);

		//Creating Log Monitor Execution Node
		try {
			org.omg.CORBA.Object obj = orb.string_to_object(monitorCorbaname);
			IComponent execNodeComp = IComponentHelper.narrow(obj);
			execNodeComp.startup();
			Object ob = execNodeComp.getFacet(EXEC_NODE_FACET);
			execNode[1] = ExecutionNodeHelper.narrow(ob);

		} catch (SystemException ex) {
			System.err.println("Erro ao conectar com o ExecutionNode " + monitorCorbaname);
			System.exit(1);
		} catch (StartupFailed e) {
			System.err.println("Startup do ExecutionNode " + monitorCorbaname + "falhou.");
			System.exit(1);
		}

		//Creating Log monitor Container
		if (!this.createContainer(CONTAINER_NAME,execNode[1])) {
			System.err.println("Erro criando o container em " + monitorCorbaname);
			return false;
		}

		//Retrieving EventChannel Container
		IComponent evContainer;
		evContainer = execNode[0].getContainer(EVCONTAINER_NAME);

		//Retrieving LogMonitor Container
		IComponent monitorContainer;
		monitorContainer = execNode[1].getContainer(CONTAINER_NAME);

		//Starting Container
		try {
			monitorContainer.startup();
		} catch (StartupFailed e) {
			System.out.println("Erro no startup do container em " + monitorCorbaname);
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
		ComponentLoader loader = ComponentLoaderHelper.narrow(monitorContainer
			.getFacet("scs::container::ComponentLoader"));
		if (loader == null) {
			System.out.println("Erro ao retornar faceta loader em " + monitorCorbaname);
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

		//Loading Log monitor Component
		ComponentHandle logmonitorHandle = null;
		ComponentId logmonitorCompId = new ComponentId();
		logmonitorCompId.name = "LogMonitor";
		logmonitorCompId.version = 1;


		logmonitorHandle = createHandle(loader, logmonitorCompId);
		if (logmonitorHandle == null) {
			return false;
		}

		logMonitorComp = logmonitorHandle.cmp;

		//Getting LogMonitor Receptacle, where event channel will connect
		IReceptacles info1 = IReceptaclesHelper.narrow(logMonitorComp.getFacetByName("infoReceptacle"));
		if( info1 == null ) {
			System.out.println("Erro ao retornar receptaculo do LogMonitor!");
			return false;
		}

		//Getting Administration Facet
		LogMonitor logMonitor = LogMonitorHelper.narrow(logMonitorComp.getFacet("scs::demos::logmonitor::LogMonitor"));
		if( logMonitor == null ) {
			System.out.println("Erro ao retornar LogMonitor!");
			return false;
		}
		logMonitor.setId(1);

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
		
			//Getting Event Channel Facet and Receptacle
			EventSink eventSink = EventSinkHelper.narrow(masterChannel.getFacetByName("EventSink"));
		
			//Connecting EventSink Facet, from Event Channel, to Log Monitor Receptacle
			try {
				info1.connect("LogMonitor", eventSink);
			} catch (InvalidName e) {
				e.printStackTrace();
			} catch (InvalidConnection e) {
				e.printStackTrace();
			} catch (AlreadyConnected e) {
				e.printStackTrace();
			} catch (ExceededConnectionLimit e) {
				e.printStackTrace();
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
		String monitorHost = "localhost";
		String monitorPort = "1050";
		String logFilename = "";
		Integer logMonitorInterval = 120000;
		
		
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
			System.out.print("Log monitor ExecutionNode Host (default is localhost): ");
			opt = br.readLine();
			if(!opt.equals("")) {
				monitorHost = opt;
			}
			opt = "";
			System.out.print("Log monitor ExecutionNode Port (default is 1050): ");
			opt = br.readLine();
			if(!opt.equals("")) {
				monitorPort = opt;
			}
			opt = "";
			System.out.print("Log file complete path (make sure you have permission): ");
			opt = br.readLine();
			if(opt.equals("")) {
				System.out.println("Invalid path");
				System.exit(1);
			}
			logFilename = opt;
			opt = "";
			System.out.print("Log monitor interval in seconds (default is 120): ");
			opt = br.readLine();
			if(!opt.equals("")) {
				logMonitorInterval = Integer.parseInt(opt)*1000;
			}
			
			long start = System.currentTimeMillis();
		
			LogMonitorApp app = new LogMonitorApp(evHost, evPort, monitorHost, monitorPort);
		
			//Sleep for debug
			// CHANGED
			Thread.sleep(10000);
			
			//Run App
			app.run(logFilename, logMonitorInterval);
			
			long end = System.currentTimeMillis();
			System.out.println("Tempo total de execucao:" + (end - start));
			app.stop();
			
			
		} catch (SystemException ex) {
			ex.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	public void run(String logfile, Integer interval ) throws InterruptedException {
		//Retrieving Log Monitor Facet
		LogMonitor log = LogMonitorHelper.narrow(logMonitorComp.getFacetByName("LogMonitor"));
		
		//Setting log file path, passed via arguments
		log.setLogFile(logfile);
		log.setMonitorInterval(interval);
		
		//Publish log into event channel
		log.publishLog();
	}

	public void stop() {
		try{
			//Retrieving Log Monitor Facet
			LogMonitor log = LogMonitorHelper.narrow(logMonitorComp.getFacetByName("LogMonitor"));
			
			//Stop tail and close tailed file
			log.setTailing(false);
			
			execNode[1].stopContainer(CONTAINER_NAME); 
			Thread.sleep(1000);
		} catch (Exception e ) {
			System.err.println("Erro ao finalizar container");
		}
	}
}