package net.floodlightcontroller.mactracker;

import java.util.Collection;
import java.util.Map;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MacTrackerThreadStart implements IFloodlightModule {
	
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;
	public MACTracker mactracker;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
	    Collection<Class<? extends IFloodlightService>> l =
	        new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IFloodlightProviderService.class);
	    return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
	    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	    logger = LoggerFactory.getLogger(MacTrackerThreadStart.class);
	    logger.info("MACThreadStart Init Updated");
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		//logger.info("MACThreadStart Start - Before Start Call");
		//logger.info("MACThreadStart,floodlightProvider---{}",floodlightProvider);
		//mactracker = new MACTracker(floodlightProvider);
		mactracker = new MACTracker();
		mactracker.set_for_thread(floodlightProvider);
		//mactracker.start();
		//logger.info("MACThreadStart Start - After Start Call");
	}

}
