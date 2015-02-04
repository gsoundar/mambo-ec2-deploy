package mambo.terasort;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.output.FileOutputCommitter;

public class MultipleFileOutputCommitter extends FileOutputCommitter {
	private static final Log LOG = LogFactory.getLog(MultipleFileOutputCommitter.class);
	private Path outputPath = null;
	
	public MultipleFileOutputCommitter(Path outputPath, TaskAttemptContext context) throws IOException {
	    super(outputPath, context);
	    if (outputPath != null) {
	        FileSystem fs = outputPath.getFileSystem(context.getConfiguration());
	        this.outputPath = fs.makeQualified(outputPath);
		}
	}

	private boolean hasOutputPath() {
		return this.outputPath != null;
	}
	
	private Path getOutputPath() {
		return this.outputPath;
	}
	

	private static class CommittedTaskFilter implements PathFilter {
	    public boolean accept(Path path) {
	        return !PENDING_DIR_NAME.equals(path.getName());
	    }
	}
	
	private FileStatus[] getAllCommittedTaskPaths(JobContext context) throws IOException {
	    
	    Path[] jobAttemptPaths = getMultipleOutputPaths(getJobAttemptPath(context));
	    FileSystem fs = jobAttemptPaths[0].getFileSystem(context.getConfiguration());
	    ArrayList<FileStatus> files = new ArrayList<FileStatus>();
	    
	    for (Path jobAttemptPath : jobAttemptPaths) {
	        FileStatus[] filesInOnePath = fs.listStatus(jobAttemptPath, new CommittedTaskFilter());
	        System.out.println(filesInOnePath.length);
	        for (FileStatus file: filesInOnePath) {
	            files.add(file);
	            System.out.println("mambo file: "+file.getPath().toString());
	        }
	    }
	    return files.toArray(new FileStatus[files.size()]);
	    
	}
	
	public void commitJob(JobContext context) throws IOException {
	    if (hasOutputPath()) {
	        Path finalOutput = getOutputPath();
	        System.out.println("mambo finaloutput: " + finalOutput);
	        FileSystem fs = finalOutput.getFileSystem(context.getConfiguration());
	        String[] paths = TeraOutputFormat.getAllOutputPaths();
	        int index = -1;
	        
	        for(FileStatus stat: getAllCommittedTaskPaths(context)) {
	            for (int i = 0; i< paths.length; i++) {
	                if (stat.getPath().toString().contains(paths[i])) {
	                    index = i;
	                    break;
	                }
	            }
	            assert(index >= 0);
	            mergePaths(fs, stat, new Path(finalOutput.toString().replace(paths[0], paths[index])));	        
	        }

	        // delete the _temporary folder and create a _done file in the o/p folder
	     
	        Path pendingJobAttemptPath = new Path(finalOutput, PENDING_DIR_NAME);
	        Path[] pendingJobAttemptPaths = getMultipleOutputPaths(pendingJobAttemptPath);
	        for (Path p : pendingJobAttemptPaths) {
	                fs.delete(p, true);
	        } 
	        

	        // True if the job requires output.dir marked on successful job.
	        // Note that by default it is set to true.
	        if (context.getConfiguration().getBoolean(SUCCESSFUL_JOB_OUTPUT_DIR_MARKER, true)) {
	          Path markerPath = new Path(outputPath, SUCCEEDED_FILE_NAME);
	          fs.create(markerPath).close();
	        }
	      } else {
	        LOG.warn("Output Path is null in commitJob()");
	      }
	}
	
	private static void mergePaths(FileSystem fs, final FileStatus from,
	          final Path to)
	        throws IOException {
	         LOG.debug("Merging data from "+from+" to "+to);
	         System.out.println("Merging data from "+from+" to "+to);
	         if(from.isFile()) {
	           if(fs.exists(to)) {
	             if(!fs.delete(to, true)) {
	               throw new IOException("Failed to delete "+to);
	             }
	           }

	           if(!fs.rename(from.getPath(), to)) {
	             throw new IOException("Failed to rename "+from+" to "+to);
	           }
	         } else if(from.isDirectory()) {
	           if(fs.exists(to)) {
	             FileStatus toStat = fs.getFileStatus(to);
	             if(!toStat.isDirectory()) {
	               if(!fs.delete(to, true)) {
	                 throw new IOException("Failed to delete "+to);
	               }
	               if(!fs.rename(from.getPath(), to)) {
	                 throw new IOException("Failed to rename "+from+" to "+to);
	               }
	             } else {
	               //It is a directory so merge everything in the directories
	               for(FileStatus subFrom: fs.listStatus(from.getPath())) {
	                 Path subTo = new Path(to, subFrom.getPath().getName());
	                 mergePaths(fs, subFrom, subTo);
	               }
	             }
	           } else {
	             //it does not exist just rename
	             if(!fs.rename(from.getPath(), to)) {
	               throw new IOException("Failed to rename "+from+" to "+to);
	             }
	           }
	         }
	      }
	
	public static Path[] getMultipleOutputPaths(Path p) {
	    String[] paths = TeraOutputFormat.getAllOutputPaths();
	    Path[] outputPaths = new Path[paths.length];
	    for (int i = 0; i < paths.length; i++) {
	        outputPaths[i] = new Path(p.toString().replace(paths[0], paths[i]));
	    }
	    return outputPaths;
	}
}
