package sid.dynamic.instruments.counters;

import org.eclipse.core.resources.IProject;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;

import sid.dynamic.instruments.Instrument;

public abstract class Counter extends Instrument {
	
	public Counter(IProject project, GraphElement graphElement) {
		super(project, graphElement);
	}
	
}
