/**
 * 
 */
package net.floodlightcontroller.openqos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.core.web.SwitchResourceBase;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.*;

/**
 * @author aagrawa5
 *
 */
public class LinkCongestion extends SwitchResourceBase implements
		IFloodlightModule {
	protected static Logger log;

	protected IFloodlightProviderService floodlightProvider;
	protected IThreadPoolService threadPool;
	private static HashMap<NodePortTuple, Long> portStats = new HashMap<NodePortTuple, Long>();

	
	public static HashMap<NodePortTuple, Long> getPortStats() {
		return portStats;
	} 

    /**
     * Poll stats task
     */
    protected SingletonTask pollStatsTask;
    protected final int POLL_STATS_TASK_INTERVAL = 200; // in ms.
    protected final int POLL_STATS_TASK_SIZE = 10; // # of ports per iteration

	
	/* (non-Javadoc)
	 * @see net.floodlightcontroller.core.module.IFloodlightModule#getModuleServices()
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.module.IFloodlightModule#getServiceImpls()
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.module.IFloodlightModule#getModuleDependencies
	 * ()
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IThreadPoolService.class);
		return l;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.floodlightcontroller.core.module.IFloodlightModule#init(net.
	 * floodlightcontroller.core.module.FloodlightModuleContext)
	 */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);

		log = LoggerFactory.getLogger(LinkCongestion.class);
		threadPool = context.getServiceImpl(IThreadPoolService.class);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.floodlightcontroller.core.module.IFloodlightModule#startUp(net.
	 * floodlightcontroller.core.module.FloodlightModuleContext)
	 */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		ScheduledExecutorService ses = threadPool.getScheduledExecutor();

		pollStatsTask = new SingletonTask(ses, new PollStatsWorker());
		pollStatsTask.reschedule(POLL_STATS_TASK_INTERVAL,
				TimeUnit.MILLISECONDS);
	}

	/*
	 * You create a request and add it to OFStatisticsRequest and send is as a
	 * query by using .sendQuery with a MessageListener so that when the switch
	 * responds, you need to process it asynchronously. So some synchronization
	 * difficulty come into play too. However all this is done and available
	 * though the REST API of floodlight, so all that you need to do is query
	 * /wm/core/switch/all/port/json for all port statistics of each port
	 * including the no of bytes received and transfered and query
	 * /wm/topology/links/json for the information on the topology, and it gives
	 * you the list of entries with the srcMAC, srcPORT and dstMAC,dstPORT. Then
	 * you need to query /wm/device/ to get the list of devices ie their MAC IDS
	 * and which switch they are connected to and at what port. Using these two
	 * JSONs you can generate the entire topology and get all the links
	 * available. Then querying /wm/core/switch/all/port/json , gives you the
	 * list of MAC IDs of the Switches and the ports and the amount /number of
	 * packets received and transmitted. By querying this every now and then you
	 * can get the bandwidth of all the given links in the entire graph and
	 * store it in a Map of a Map of Double.
	 */

	/**
	 * 
	 * @param sw
	 *            the switch object that we wish to get flows from
	 * @param outPort
	 *            the output action port we wish to find flows with
	 * @return a list of OFFlowStatisticsReply objects or essentially flows
	 */
	public void computePortStats(IOFSwitch sw) { // Change what the list returns to your stat type

		List<OFStatistics> values = null;
		Future<List<OFStatistics>> future;
		long bytes_rcvd;
		Collection<Short> portsOnSwitch = sw.getEnabledPortNumbers();
		int requestLength;
//		LinkDiscoveryManager ldm = new LinkDiscoveryManager();
//		Map<Long, Set<Link>> switchLinks = ldm.getSwitchLinks();
		OFPortStatisticsRequest specificReq = new OFPortStatisticsRequest();
		for (Short port: portsOnSwitch) {
			
			NodePortTuple nodeport = new NodePortTuple(sw.getId(),port);
			OFStatisticsRequest req = new OFStatisticsRequest(); // Statistics request object for getting ports stats
			req.setStatisticType(OFStatisticsType.PORT); // Change to your stat type here, and modify the following variables(eg. remove outPort since that's flow stat specific)
			requestLength = req.getLengthU();
			specificReq.setPortNumber(port);
			
			req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
			requestLength += specificReq.getLength();
			req.setLengthU(requestLength);

			try {
			
				future = sw.queryStatistics(req);
				values = future.get(10, TimeUnit.SECONDS);
				
				if(values != null){
					for(OFStatistics stat : values){
						
						bytes_rcvd = ((OFPortStatisticsReply)stat).getReceiveBytes();
						/*log.error("ANKIT>>> Retrieved port statistics from switch {} , port number {}, received_bytes {}",
								new Object[] {sw,
								((OFPortStatisticsReply)stat).getPortNumber(),
								((OFPortStatisticsReply)stat).getReceiveBytes()
						});*/
						
						//long DataPathId = switchbase.getId();
						portStats.put(nodeport, bytes_rcvd);
						//log.info("Port stats --->" +nodeport.getNodeId()+ "---->"+nodeport.getPortId() + "----->" + 
						//		bytes_rcvd);
					}
					
				}
			} catch (Exception e) {
				log.error("Failure retrieving statistics from switch " + sw, e);

			}
		}
	}


	protected void pollStats() {
		IOFSwitch sw;
		Set<Long> allSwDpids = floodlightProvider.getAllSwitchDpids();
		for (Long dpid : allSwDpids) {
			sw = floodlightProvider.getSwitch(dpid);
			computePortStats(sw);
		}

		//log.error("Have a list of portStats " + portStats);

		//log.error("ANKIT>> Thread runs!");

	}

	protected class PollStatsWorker implements Runnable {
		@Override
		public void run() {
			try {
				pollStats();
			} catch (Exception e) {
				log.error("ANKIT>>> Error in poll stats worker thread", e);
			} finally {
				pollStatsTask.reschedule(POLL_STATS_TASK_INTERVAL,
						TimeUnit.MILLISECONDS);
			}
		}
	}

}
