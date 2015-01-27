/**
 * 
 */
package net.floodlightcontroller.mactracker;

import net.floodlightcontroller.core.IFloodlightProviderService;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import net.floodlightcontroller.packet.Ethernet;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.statistics.OFAggregateStatisticsReply;
import org.openflow.protocol.statistics.OFAggregateStatisticsRequest;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFQueueStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.web.AllSwitchStatisticsResource;
import net.floodlightcontroller.core.web.SwitchResourceBase.REQUESTTYPE;

/**
 * @author aagrawa5
 *
 */
public class MACTracker extends Thread implements IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected Set macAddresses;
	protected static Logger logger;

	protected List<OFStatistics> getSwitchStatistics(long switchId,
			OFStatisticsType statType) {

		IOFSwitch sw = floodlightProvider.getSwitch(switchId);
		Future<List<OFStatistics>> future;
		List<OFStatistics> values = null;
		if (sw != null) {
			OFStatisticsRequest req = new OFStatisticsRequest();
			req.setStatisticType(statType);
			int requestLength = req.getLengthU();
			if (statType == OFStatisticsType.FLOW) {
				OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
				OFMatch match = new OFMatch();
				match.setWildcards(0xffffffff);
				specificReq.setMatch(match);
				specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
				specificReq.setTableId((byte) 0xff);
				req.setStatistics(Collections
						.singletonList((OFStatistics) specificReq));
				requestLength += specificReq.getLength();
			} else if (statType == OFStatisticsType.AGGREGATE) {
				OFAggregateStatisticsRequest specificReq = new OFAggregateStatisticsRequest();
				OFMatch match = new OFMatch();
				match.setWildcards(0xffffffff);
				specificReq.setMatch(match);
				specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
				specificReq.setTableId((byte) 0xff);
				req.setStatistics(Collections
						.singletonList((OFStatistics) specificReq));
				requestLength += specificReq.getLength();
			} else if (statType == OFStatisticsType.PORT) {
				OFPortStatisticsRequest specificReq = new OFPortStatisticsRequest();
				specificReq.setPortNumber(OFPort.OFPP_NONE.getValue());
				req.setStatistics(Collections
						.singletonList((OFStatistics) specificReq));
				requestLength += specificReq.getLength();
			} else if (statType == OFStatisticsType.QUEUE) {
				OFQueueStatisticsRequest specificReq = new OFQueueStatisticsRequest();
				specificReq.setPortNumber(OFPort.OFPP_ALL.getValue());
				// LOOK! openflowj does not define OFPQ_ALL! pulled this from
				// openflow.h
				// note that I haven't seen this work yet though...
				specificReq.setQueueId(0xffffffff);
				req.setStatistics(Collections
						.singletonList((OFStatistics) specificReq));
				requestLength += specificReq.getLength();
			} else if (statType == OFStatisticsType.DESC
					|| statType == OFStatisticsType.TABLE) {
				// pass - nothing todo besides set the type above
			}
			req.setLengthU(requestLength);
			try {
				future = sw.queryStatistics(req);
				values = future.get(10, TimeUnit.SECONDS);
			} catch (Exception e) {
				logger.error("Failure retrieving statistics from switch " + sw,
						e);
			}
		}
		return values;
	}

	protected List<OFStatistics> getSwitchStatistics(String switchId,
			OFStatisticsType statType) {
		return getSwitchStatistics(HexString.toLong(switchId), statType);
	}

	protected OFFeaturesReply getSwitchFeaturesReply(long switchId) {

		IOFSwitch sw = floodlightProvider.getSwitch(switchId);
		Future<OFFeaturesReply> future;
		OFFeaturesReply featuresReply = null;
		if (sw != null) {
			try {
				future = sw.querySwitchFeaturesReply();
				featuresReply = future.get(10, TimeUnit.SECONDS);
			} catch (Exception e) {
				logger.error("Failure getting features reply from switch" + sw,
						e);
			}
		}

		return featuresReply;
	}

	protected OFFeaturesReply getSwitchFeaturesReply(String switchId) {
		return getSwitchFeaturesReply(HexString.toLong(switchId));
	}

	protected class GetConcurrentStatsThread extends Thread {
		private List<OFStatistics> switchReply;
		private long switchId;
		private OFStatisticsType statType;
		private REQUESTTYPE requestType;
		private OFFeaturesReply featuresReply;

		public GetConcurrentStatsThread(long switchId, REQUESTTYPE requestType,
				OFStatisticsType statType) {
			this.switchId = switchId;
			this.requestType = requestType;
			this.statType = statType;
			this.switchReply = null;
			this.featuresReply = null;
		}

		public List<OFStatistics> getStatisticsReply() {
			return switchReply;
		}

		public OFFeaturesReply getFeaturesReply() {
			return featuresReply;
		}

		public long getSwitchId() {
			return switchId;
		}

		@Override
		public void run() {
			if ((requestType == REQUESTTYPE.OFSTATS) && (statType != null)) {
				switchReply = getSwitchStatistics(switchId, statType);
			} else if (requestType == REQUESTTYPE.OFFEATURES) {
				featuresReply = getSwitchFeaturesReply(switchId);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.floodlightcontroller.core.IListener#getName()
	 */

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.IListener#isCallbackOrderingPrereq(java
	 * .lang.Object, java.lang.String)
	 */

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.IListener#isCallbackOrderingPostreq(java
	 * .lang.Object, java.lang.String)
	 */

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.floodlightcontroller.core.module.IFloodlightModule#getModuleServices
	 * ()
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
		macAddresses = new ConcurrentSkipListSet<Long>();
		logger = LoggerFactory.getLogger(MACTracker.class);
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
		logger.info("Inside Startup for MacTRACKER");
		// floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.floodlightcontroller.core.IOFMessageListener#receive(net.
	 * floodlightcontroller.core.IOFSwitch, org.openflow.protocol.OFMessage,
	 * net.floodlightcontroller.core.FloodlightContext)
	 */

	@SuppressWarnings({ "unused", "unchecked" })
	public void run() {

		logger.info("Inside Run - MacTracker");
		while (true) {
			logger.info("Inside While - MacTracker");
			HashMap<String, Object> model = new HashMap<String, Object>();
			
			model = (HashMap<String, Object>) stat_data("aggregate");
			List<OFAggregateStatisticsReply> ofreplylist;
			// OFAggregateStatisticsReply ofreply;
			for (String key : model.keySet()) {

				logger.info("AGGREGATE STATS->{} -> {}", key, model.get(key)
						.toString());
				ofreplylist = (List<OFAggregateStatisticsReply>) model.get(key);
				for (OFAggregateStatisticsReply ofreply : ofreplylist) {
					logger.info("OFAggregateStatisticsReply->getByteCount "
							+ ofreply.getByteCount());
					logger.info("OFAggregateStatisticsReply->getFlowCount "
							+ ofreply.getFlowCount());
					logger.info("OFAggregateStatisticsReply->getPacketCount "
							+ ofreply.getPacketCount());
				}

			}
			try {
				Thread.sleep(10000);
				logger.info("After sleep - MacTracker");
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	// @SuppressWarnings("unchecked")

	/*
	 * public net.floodlightcontroller.core.IListener.Command receive( IOFSwitch
	 * sw, OFMessage msg, FloodlightContext cntx) { Ethernet eth =
	 * IFloodlightProviderService.bcStore.get(cntx,
	 * IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
	 * 
	 * Long sourceMACHash = Ethernet.toLong(eth.getSourceMACAddress()); if
	 * (!macAddresses.contains(sourceMACHash)) {
	 * macAddresses.add(sourceMACHash);
	 * logger.info("MAC Address: {} seen on switch: {}",
	 * HexString.toHexString(sourceMACHash), sw.getId()); } HashMap<String,
	 * Object> model = new HashMap<String, Object>(); model = (HashMap<String,
	 * Object>) stat_data("AGGREGATE"); for (String key : model.keySet()) {
	 * 
	 * logger.info("AGGREGATE STATS->{} -> {}", key, model.get(key)
	 * .toString()); } return Command.CONTINUE; }
	 */
	public Map<String, Object> stat_data(String stat_typ) {
		// switchstatres = new AllSwitchStatisticsResource();
		HashMap<String, Object> model = new HashMap<String, Object>();
		model = (HashMap<String, Object>) retrieveInternal(stat_typ);
		return model;
	}

	public Map<String, Object> retrieveInternal(String statType) {
		HashMap<String, Object> model = new HashMap<String, Object>();

		OFStatisticsType type = null;
		REQUESTTYPE rType = null;

		if (statType.equals("port")) {
			type = OFStatisticsType.PORT;
			rType = REQUESTTYPE.OFSTATS;
		} else if (statType.equals("queue")) {
			type = OFStatisticsType.QUEUE;
			rType = REQUESTTYPE.OFSTATS;
		} else if (statType.equals("flow")) {
			type = OFStatisticsType.FLOW;
			rType = REQUESTTYPE.OFSTATS;
		} else if (statType.equals("aggregate")) {
			type = OFStatisticsType.AGGREGATE;
			logger.info("AGGREGATE - retrieveInternal");
			rType = REQUESTTYPE.OFSTATS;
		} else if (statType.equals("desc")) {
			type = OFStatisticsType.DESC;
			rType = REQUESTTYPE.OFSTATS;
		} else if (statType.equals("table")) {
			type = OFStatisticsType.TABLE;
			rType = REQUESTTYPE.OFSTATS;
		} else if (statType.equals("features")) {
			rType = REQUESTTYPE.OFFEATURES;
		} else {
			return model;
		}

		Set<Long> switchDpids = floodlightProvider.getAllSwitchDpids();
		List<GetConcurrentStatsThread> activeThreads = new ArrayList<GetConcurrentStatsThread>(
				switchDpids.size());
		List<GetConcurrentStatsThread> pendingRemovalThreads = new ArrayList<GetConcurrentStatsThread>();
		GetConcurrentStatsThread t;
		for (Long l : switchDpids) {
			t = new GetConcurrentStatsThread(l, rType, type);
			activeThreads.add(t);
			t.start();
		}

		// Join all the threads after the timeout. Set a hard timeout
		// of 12 seconds for the threads to finish. If the thread has not
		// finished the switch has not replied yet and therefore we won't
		// add the switch's stats to the reply.
		for (int iSleepCycles = 0; iSleepCycles < 12; iSleepCycles++) {
			for (GetConcurrentStatsThread curThread : activeThreads) {
				if (curThread.getState() == State.TERMINATED) {
					if (rType == REQUESTTYPE.OFSTATS) {
						model.put(
								HexString.toHexString(curThread.getSwitchId()),
								curThread.getStatisticsReply());
					} else if (rType == REQUESTTYPE.OFFEATURES) {
						model.put(
								HexString.toHexString(curThread.getSwitchId()),
								curThread.getFeaturesReply());
					}
					pendingRemovalThreads.add(curThread);
				}
			}

			// remove the threads that have completed the queries to the
			// switches
			for (GetConcurrentStatsThread curThread : pendingRemovalThreads) {
				activeThreads.remove(curThread);
			}
			// clear the list so we don't try to double remove them
			pendingRemovalThreads.clear();

			// if we are done finish early so we don't always get the worst case
			if (activeThreads.isEmpty()) {
				break;
			}

			// sleep for 1 s here
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.error("Interrupted while waiting for statistics", e);
			}
		}

		return model;
	}

	public void set_for_thread(IFloodlightProviderService floodlightProvider2) {
		// TODO Auto-generated method stub
		this.floodlightProvider = floodlightProvider2;

	}

}
