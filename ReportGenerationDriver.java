package com.amex.ccsg.ipartner;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.print.attribute.HashAttributeSet;
        
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
        
public class ReportGenerationDriver {
        
 public static class Map extends Mapper<LongWritable, Text, Text, Text> {
	 
	private java.util.Map<String, Integer> HeaderMap;

    public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        String line = value.toString();
        String[] tokens = line.split("\t");
        java.util.Map<Integer, String> valuesMap = new HashMap<Integer, String>();
        
        int tokenindex = 1;
        for(String token : tokens){
    		valuesMap.put(tokenindex,token);
    		tokenindex++;
    	}
        
        if(valuesMap.get(HeaderMap.get("offer_type")).equalsIgnoreCase("PS")){
        	context.getCounter("Map Side Counters","PS Records").increment(1);
        	
        	String pin 		 = valuesMap.get(HeaderMap.get("pin"));
        	String mail_date = valuesMap.get(HeaderMap.get("mail_date"));
        	String as_of_dt  = valuesMap.get(HeaderMap.get("as_of_dt"));
        	String prod_group = valuesMap.get(HeaderMap.get("prod_group"));
        	String ia_code = valuesMap.get(HeaderMap.get("ia_code"));
        	String emerg_premium = valuesMap.get(HeaderMap.get("emerg_premium"));
        	
        	if((emerg_premium == null) || emerg_premium.equalsIgnoreCase("")) emerg_premium = "NA";
        	
        	context.write(new Text(pin), new Text(mail_date+"\t"+as_of_dt+"\t"+prod_group+"\t"+ia_code+"\t"+emerg_premium));
        }else{
        	context.getCounter("Map Side Counters","NPA records").increment(1);
        }
               
    }
    
    public void setup(Context context){
    	
    	HeaderMap = new HashMap<String, Integer>();
    	String[] titles = "pin	bin	as_of_dt	mail_drop_dt	mail_ship_dt	mail_date	poid	spid	primary_source_code	campaign_code	cell_id	cell_title	offer_title	bus_unit	offer_type	channel	camp_type	ia_code	ia_description	prod_group	card_group	emerg_premium	model_validation	first_year_free	acquisition_pts	acquisition_stmt_credit	spend_threshold	threshold_duration	secondary_fpb_pts	secondary_fpb_stmt_credit	secondary_threshold_duration	intro_apr	bt_apr	goto_apr	step	cell_waveid	appstat".split("\t");
    	int titleIndex = 1;
    	for(String title : titles){
    		HeaderMap.put(title, titleIndex);
    		titleIndex++;
    	}
    	
    }
 } 
        
 public static class Reduce extends Reducer<Text, Text, Text, Text> {
	 
    private final static SimpleDateFormat frmt = new SimpleDateFormat("yyyyMMdd");
    
    private static Calendar February = Calendar.getInstance();
    private static Calendar July = Calendar.getInstance();
	 
    public void reduce(Text key, Iterable<Text> values, Context context) 
      throws IOException, InterruptedException { 
    	
    	February.set(2014, 1, 1);
    	July.set(2014, 6, 31);
    	int CCSG_PS_L6month_count = 0;
        ArrayList<Date> dateList = new ArrayList<Date>();
        java.util.Map<Date, String> dateVarsMap = new HashMap<Date, String>();
        for (Text val : values) {
        	try {
        		String[] valTokens = val.toString().split("\t");
        		dateList.add(frmt.parse(valTokens[0]));
        		dateVarsMap.put(frmt.parse(valTokens[0]),valTokens[1]+"\t"+valTokens[2]+"\t"+valTokens[3]+"\t"+valTokens[4]);        	
				if(frmt.parse(valTokens[0]).after(February.getTime()) && frmt.parse(valTokens[0]).before(July.getTime())) {
					System.out.println(key +"     Incrementing for =========="+frmt.parse(valTokens[0]));
					CCSG_PS_L6month_count++;
				}
			} catch (ParseException e) {
				
			}
        }
        if(dateList.size() > 0){
            Collections.sort(dateList);
            Date maxDate = dateList.get(dateList.size()-1);
            String maxDateStr = frmt.format(maxDate);
            
            //context.write(key, new Text(maxDateStr+"\t"+dateVarsMap.get(maxDate)+"\t"+CCSG_PS_L6month_count));
            context.write(key, new Text(dateVarsMap.get(maxDate)+"\t"+CCSG_PS_L6month_count));
        }else{
        	context.getCounter("Reduce Side Counters","Date List Zero").increment(1);
        }

    }
 }
        
 public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    conf.set("mapred.job.queue.name","AETPMC");   
    Job job = new Job(conf, "CCSG CH Solicitation");
    job.setJarByClass(ReportGenerationDriver.class);
    
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(Text.class);
        
    job.setMapperClass(Map.class);
    job.setReducerClass(Reduce.class);
        
    job.setInputFormatClass(TextInputFormat.class);
    job.setOutputFormatClass(TextOutputFormat.class);
        
    FileInputFormat.addInputPath(job, new Path(args[0]));
    
    FileSystem.getLocal(conf).delete(new Path(args[1]), true);
    
    FileOutputFormat.setOutputPath(job, new Path(args[1]));
    
    job.setNumReduceTasks(100);
        
    job.waitForCompletion(true);
 }
        
}
