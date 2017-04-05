package edu.uta.cse6331;

import java.io.*;
import java.util.*;
import java.lang.*;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;

class Vertex implements Writable 
{
    public short tag;
	public long group;
    public long vid;
    public long size;
	public Vector<Long> adjacent = new Vector<Long>();
	Vertex () {}

    Vertex (short t,long g,long v,Vector ad) 
    {
        tag=t; group = g;vid = v; adjacent = ad; size=ad.size();
    }
	
	Vertex (short t,long g) 
	{
        tag=t; group = g;
    }

    public void write ( DataOutput out ) throws IOException 
    {
        out.writeShort(tag);
		out.writeLong(group);
        out.writeLong(vid);
        out.writeLong(size);
        for (int i=0;i<adjacent.size();i++)
        {
        	out.writeLong(adjacent.get(i));
        }
		
    }

    public void readFields ( DataInput in ) throws IOException 
    {
    	tag = in.readShort();
		group = in.readLong();
        vid = in.readLong();
        size=in.readLong();
        adjacent=new Vector<Long>();
        for (long y=0;y<size;y++)
        {
        	adjacent.add(in.readLong());
        }
    }

    
}

public class Graph 
{	
	public static class InputMapper extends Mapper<Object,Text,LongWritable,Vertex > 
	{        
        @Override
        public void map ( Object key, Text value, Context context )
                        throws IOException, InterruptedException {
            Vector<Long> adj = new Vector<Long>();
            Scanner s = new Scanner(value.toString()).useDelimiter(",");
            long vid = s.nextLong();
			
			while(s.hasNextLong())
			{
				long ele = s.nextLong();
				adj.addElement(ele);
			}
            context.write(new LongWritable(vid),new Vertex((short)0,vid,vid,adj));
            s.close();
        }
    }

    public static class InputReducer extends Reducer<LongWritable,Vertex,LongWritable,Vertex> 
    {        
        @Override
        public void reduce ( LongWritable key, Iterable<Vertex> values, Context context )
                           throws IOException, InterruptedException {

            for (Vertex v: values)
			{				
				context.write(key,new Vertex(v.tag,v.group,v.vid,v.adjacent));						
			}
		}				
    }
		
	public static class SecondMapper extends Mapper<LongWritable,Vertex,LongWritable,Vertex > 
	{	
        @Override       
        public void map ( LongWritable key, Vertex value, Context context )
                        throws IOException, InterruptedException {
            context.write(new LongWritable(value.vid),value);
            
            for (long n:value.adjacent)
			{
				context.write(new LongWritable(n),new Vertex((short)1,value.group));
			}
        }
    }
	
	public static class SecondReducer extends Reducer<LongWritable,Vertex,LongWritable,Vertex> 
	{        
        @Override
        public void reduce ( LongWritable key, Iterable<Vertex> values, Context context )
                           throws IOException, InterruptedException {
            Vertex vr=new Vertex();
			long max = Long.MAX_VALUE;
            for (Vertex v: values)
			{	
				if(v.tag == 0)
				{
					vr = new Vertex(v.tag,v.group,v.vid,v.adjacent);
				}
				if (v.group < max)
				{
					max = v.group;
				}
			}
			context.write(new LongWritable(max),new Vertex((short)0,max,vr.vid,vr.adjacent));						
		}
	}  

	public static class ThirdMapper extends Mapper<LongWritable,Vertex,LongWritable,IntWritable > 
	{
        @Override
        public void map ( LongWritable key, Vertex value, Context context )
                        throws IOException, InterruptedException {
            context.write(new LongWritable(value.group),new IntWritable(1));
            
        }
    }		

	public static class ThirdReducer extends Reducer<LongWritable,IntWritable,LongWritable,LongWritable> 
	{        
        @Override
        public void reduce ( LongWritable key, Iterable<IntWritable> values, Context context )
                           throws IOException, InterruptedException {
			long m = 0L;
			for (IntWritable v: values)
				{
					long j =Long.valueOf(v.get());
					m = m + j;
				}
				context.write(key,new LongWritable(m));
			}
		}	

   public static void main ( String[] args ) throws Exception 
   {
        Job job1 = Job.getInstance();
        job1.setJobName("Graph reading");
        job1.setJarByClass(Graph.class);
        job1.setOutputKeyClass(LongWritable.class);
        job1.setOutputValueClass(Vertex.class);
        job1.setMapOutputKeyClass(LongWritable.class);
        job1.setMapOutputValueClass(Vertex.class);        
        job1.setOutputFormatClass(SequenceFileOutputFormat.class);
        MultipleInputs.addInputPath(job1,new Path(args[0]),TextInputFormat.class,InputMapper.class);
        FileOutputFormat.setOutputPath(job1,new Path(args[1]));
		job1.waitForCompletion(true);

		Path inpath =new Path(args[1]);
		Path outpath =null;
		for( int i=1;i<=5;i++)
		{
		Job job2 = Job.getInstance();
		outpath = new Path(args[1]+"/f"+i);
		job2.setJobName("Mapping Topography");
		job2.setJarByClass(Graph.class);
		job2.setOutputKeyClass(LongWritable.class);
		job2.setOutputValueClass(Vertex.class);
		job2.setMapOutputKeyClass(LongWritable.class);
        job2.setMapOutputValueClass(Vertex.class);
		job2.setReducerClass(SecondReducer.class);
        job2.setOutputFormatClass(SequenceFileOutputFormat.class);
		MultipleInputs.addInputPath(job2,inpath,SequenceFileInputFormat.class,SecondMapper.class);
		FileOutputFormat.setOutputPath(job2,outpath);
		inpath=outpath;
		job2.waitForCompletion(true);
		}		
		
		Job job3 = Job.getInstance();
		job3.setJobName("Graph Size");
		job3.setJarByClass(Graph.class);
		job3.setOutputKeyClass(LongWritable.class);
		job3.setOutputValueClass(LongWritable.class);
		job3.setMapOutputKeyClass(LongWritable.class);
        job3.setMapOutputValueClass(IntWritable.class);
		job3.setReducerClass(ThirdReducer.class);
        job3.setOutputFormatClass(TextOutputFormat.class);
		MultipleInputs.addInputPath(job3,new Path(args[1]+"/f5"),SequenceFileInputFormat.class,ThirdMapper.class);
		FileOutputFormat.setOutputPath(job3,new Path(args[2]));
		job3.waitForCompletion(true);
    }	
		
}