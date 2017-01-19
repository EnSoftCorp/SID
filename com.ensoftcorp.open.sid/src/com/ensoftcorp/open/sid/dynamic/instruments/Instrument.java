package com.ensoftcorp.open.sid.dynamic.instruments;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;

public abstract class Instrument {

	protected GraphElement graphElement;
	protected IProject project = null;
	
	/**
	 * Constructs an instrument to be inserted
	 * @param project The project to insert the instrument into (this will likely be a clone of the version that was indexed)
	 * @param graphElement
	 */
	public Instrument(IProject project, GraphElement graphElement){
		if(!graphElement.tags().containsAny(getSupportedGraphElements())){
			throw new IllegalArgumentException("This instrument does not support this type of graph element. Supports: " + Arrays.toString(getSupportedGraphElements()));
		}
		
		this.graphElement = graphElement;
		this.project = project;
	}
	
	public GraphElement getGraphElement() {
		return graphElement;
	}
	
	public abstract String getMeasurementKeyName();
	
	public abstract String[] getSupportedGraphElements();

	public abstract boolean performInstrumentation();
	
	/**
	 * Given a file, an offset, and a String, this method inserts the String at the given offset in the file
	 * This method automatically adjusts the offset to account for previous edits of the original file and selects
	 * the corresponding file in the project to edit
	 * @param file The original file
	 * @param offset The original file offset
	 * @param content String 
	 * @throws IOException
	 * @throws CoreException 
	 */
	protected void insert(IFile file, long offset, String content) throws IOException, CoreException {
		// figure out which project to make the edit on
		if(!file.getProject().equals(project)){
			file = project.getFile(file.getProjectRelativePath());
			if(!file.exists()){
				throw new IllegalArgumentException("Resource " + file.getProjectRelativePath().toOSString() + " does not exist for this project");
			}
		}
		
		// edit file
		EditEngine.getInstance().insert(file, offset, content.getBytes());

		// just have to refresh depth 1 because this is a single file
		file.refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
	}

}
