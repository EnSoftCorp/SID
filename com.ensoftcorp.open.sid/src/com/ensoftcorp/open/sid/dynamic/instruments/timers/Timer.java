package com.ensoftcorp.open.sid.dynamic.instruments.timers;

import org.eclipse.core.resources.IProject;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.open.sid.dynamic.instruments.Instrument;

public abstract class Timer extends Instrument {
	
	public Timer(IProject project, GraphElement graphElement) {
		super(project, graphElement);
	}
	
}
