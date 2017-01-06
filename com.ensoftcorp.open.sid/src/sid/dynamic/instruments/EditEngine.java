package sid.dynamic.instruments;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.eclipse.core.resources.IFile;

/**
 * A singleton class to manage a series of edits to multiple files
 * 
 * @author Ben Holland
 */
public class EditEngine {
	
	private static EditEngine instance = null;

	private EditEngine() {}

	public static EditEngine getInstance() {
		if (instance == null) {
			instance = new EditEngine();
		}
		return instance;
	}
	
	// keeps track of the new offsets for the original offsets during file edits
	private HashMap<String,SortedArrayList<Edit>> offsetAdjustments = new HashMap<String,SortedArrayList<Edit>>();
	
	// helper method to retrieve the edits list for a given file
	// if there are no edits, an empty edits list is lazily created
	private SortedArrayList<Edit> getEdits(IFile file) throws IOException {
		String cononicalFilePath = file.getLocation().toFile().getCanonicalPath();
		if(!offsetAdjustments.containsKey(cononicalFilePath)){
			offsetAdjustments.put(cononicalFilePath, new SortedArrayList<Edit>());
		}
		return offsetAdjustments.get(cononicalFilePath);
	}
	
	public void reset(){
		offsetAdjustments = new HashMap<String,SortedArrayList<Edit>>();
	}
	
	/**
	 * Given a file, an offset, and some bytes, this method inserts the bytes at the given offset in the file
	 * Reference: https://stackoverflow.com/questions/289965/inserting-text-into-an-existing-file-via-java
	 * @param file
	 * @param offset
	 * @param content
	 * @throws IOException
	 */
	public synchronized void insert(IFile file, long offset, byte[] content) throws IOException {
		// get the adjusted offset
		long adjustedOffset = offset;
		SortedArrayList<Edit> edits = getEdits(file);
		for(int i=0; i<edits.size(); i++){
			if(edits.get(i).getOffset() > offset){
				break;
			} else {
				adjustedOffset += edits.get(i).getLength();
			}
		}

		// make edits to file
		String filePath = file.getLocation().toFile().getCanonicalPath();
		RandomAccessFile raf = new RandomAccessFile(new File(filePath), "rw");
		File tmpFile = new File(filePath + "~");
		RandomAccessFile rtemp = new RandomAccessFile(tmpFile, "rw");
		long fileSize = raf.length();
		FileChannel sourceChannel = raf.getChannel();
		FileChannel targetChannel = rtemp.getChannel();
		sourceChannel.transferTo(adjustedOffset, (fileSize - adjustedOffset), targetChannel);
		sourceChannel.truncate(adjustedOffset);
		raf.seek(adjustedOffset);
		raf.write(content);
		long newOffset = raf.getFilePointer();
		targetChannel.position(0L);
		sourceChannel.transferFrom(targetChannel, newOffset, (fileSize - adjustedOffset));
		sourceChannel.close();
		targetChannel.close();
		rtemp.close();
		raf.close();
		tmpFile.delete();
		
		// update offset adjustments
		edits.insert(new Edit(offset, content));
	}
	
	/**
	 * Represents an edit to the file
	 * @author Ben Holland
	 */
	private static class Edit implements Comparable<Edit> {
		private long offset;
		private byte[] content;
		
		public Edit(long offset, byte[] content){
			this.offset = offset;
			this.content = content;
		}
		
		public long getOffset(){
			return offset;
		}
		
		public long getLength(){
			return content.length;
		}
		
		@Override
		public int compareTo(Edit edit) {
			return Long.compare(this.offset, edit.offset);
		}
		
		@Override
		public String toString() {
			return "Edit [offset=" + offset + ", content=" + new String(content) + ", length=" + content.length + "]";
		}
	}
	
	/**
	 * A sorted ArrayList
	 * @param <T>
	 * 
	 * Reference: http://stackoverflow.com/a/4031849/475329
	 */
	private static class SortedArrayList<T> extends ArrayList<T> {

		private static final long serialVersionUID = 1L;

		@SuppressWarnings("unchecked")
	    public void insert(T value) {
	        add(value);
	        Comparable<T> cmp = (Comparable<T>) value;
	        for (int i = size()-1; i > 0 && cmp.compareTo(get(i-1)) < 0; i--){
	            Collections.swap(this, i, i-1);
	        }
	    }
	}

}
