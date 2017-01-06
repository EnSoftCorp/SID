package sid.setup;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.internal.wizards.datatransfer.ZipLeveledStructureProvider;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;
import org.osgi.framework.Bundle;

import sid.log.Log;

@SuppressWarnings({"restriction", "rawtypes"})
public class ProjectImporter {
	
	public static IProject importProject(IProgressMonitor monitor, Bundle bundle, String bundlePath, String newProjectName) throws InvocationTargetException, InterruptedException, CoreException, IOException {
		return new ProjectImporter().importProjectFromArchive(monitor, bundle, bundlePath, newProjectName);
	}
	
	private ProjectImporter() {}
	
	/**
	 * 
	 * @param monitor
	 * @param bundle The bundle which stores the archived project
	 * @param bundlePath bundle-relative path to archive
	 * @param newProjectName
	 * @return
	 * @throws IOException
	 * @throws InvocationTargetException
	 * @throws InterruptedException
	 * @throws CoreException
	 */
	public IProject importProjectFromArchive(IProgressMonitor monitor,
			Bundle bundle, String bundlePath, String newProjectName) throws IOException,
			InvocationTargetException, InterruptedException, CoreException {
		
		URL entry = bundle.getEntry(bundlePath);
		if (entry == null) {
			throw new IllegalStateException("Archive not found: " + bundlePath);
		}
		URL fileURL = FileLocator.toFileURL(entry);
		
		File pathZip;
		try {
			// this constructor ensures the URI is correctly escaped
			// http://stackoverflow.com/questions/14676966/escape-result-of-filelocator-resolveurl/14677157#14677157
				
			URI fileURI = new URI(fileURL.getProtocol(), fileURL.getPath(), null);
			
			pathZip = URIUtil.toFile(fileURI);
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Archive cannot be loaded", e);
		}
		
		return importProjectFromArchive(monitor, pathZip, newProjectName);
	}
	
	private IProject importProjectFromArchive(IProgressMonitor monitor, File pathZip, String newProjectName) throws InvocationTargetException, InterruptedException, CoreException, IOException {
//		http://stackoverflow.com/questions/12484128/how-do-i-import-an-eclipse-project-from-a-zip-file-programmatically
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IProjectDescription newProjectDescription = workspace.newProjectDescription(newProjectName);
		IProject newProject = workspace.getRoot().getProject(newProjectName);
		
		if (newProject.exists()) {
			final String message = "The project '" + newProject.getName() + "' has already been imported; please delete it first if you want an updated version.  Proceeding with old version in the workspace.";
			Log.info(message);
			UIJob hey = new UIJob("Project already imported") {
				
				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					MessageDialog.openInformation(getDisplay().getActiveShell(), "", message);
					return Status.OK_STATUS;
				}
			};
			hey.schedule();
			
			return newProject;
		}
		
		newProject.create(newProjectDescription, null);
		newProject.open(null);
		

		IOverwriteQuery overwriteQuery = new IOverwriteQuery() {
		    public String queryOverwrite(String file) { return ALL; }
		};
		ZipLeveledStructureProvider provider = new ZipLeveledStructureProvider(new ZipFile(pathZip));
		
		List<Object> fileSystemObjects = new ArrayList<Object>();
		List children = provider.getChildren(provider.getRoot());
		Iterator itr = children.iterator();
		ZipEntry source = null;
		while (itr.hasNext()) {
		    ZipEntry nextElement = (ZipEntry) itr.next();
		    
		    // have to find the ZipEntry instance created by the Provider, because .equals() is not defined using the entry name
		    if ((newProjectName + "/").equals(nextElement.getName())) {
		    	source = nextElement;
		    }
		    
		    // NOTE: ImportOperation will fail on empty directories; ignore /bin as a stopgap
		    if (nextElement.toString().contains("/bin"))
		    	continue;
		    
			fileSystemObjects.add(nextElement);
		}
		if (source == null){
			throw new RuntimeException("Project not found in archive");
		}
		
		ImportOperation importOperation = new ImportOperation(newProject.getFullPath(), source, provider, overwriteQuery, fileSystemObjects);
		importOperation.setCreateContainerStructure(false);
		importOperation.run(monitor);
		
		return newProject;
	}

}
