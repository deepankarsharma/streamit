/**
 *
 */

import streamit.*;
import java.lang.*;
import streamit.io.*;
import java.io.*;

class EncoderInput
{

    //member variables!
    public static int mInputLength = 4;


    public int[] readFile() 
  {
    int[] input = new int[mInputLength];
    try
    {
	File f1 = new File("CrcEncoderInput1");
	java.io.FileReader fr = new java.io.FileReader(f1);
	BufferedReader br = new BufferedReader(fr);
	//DataInputStream data = new DataInputStream(new FileInputStream(f1));
	//read the sucker!
	//int[] input = new int[4];
	for(int i = 0; i < input.length; i++)
	    {
		String j = br.readLine();
		
		if (j.equals("1"))
		    {
			input[i] = 1;
		    }
		else
		    { 
			input[i] = 0;
		    }		
		
	    }
	br.close();
    }
    catch (IOException e)
    {
	System.out.println("Blah! IO error!");
    }

    return input;
  }//readFile

}//class EncoderInput

class AdditionFilter extends Filter
{
    //inputs:
    int mInitFeedbackData;
    int mShiftRegisterInput;
    //outputs
    //output 1: mInitFeedbackData
    int mAdditionOutput;


    public void init()
    {
	input = new Channel(Integer.TYPE, 2);
	output = new Channel(Integer.TYPE, 2);
    }

    public void work()
    {
	mInitFeedbackData = input.popInt();
	mShiftRegisterInput = input.popInt();
	
	if(((mInitFeedbackData == 1) && (mShiftRegisterInput == 1)) || ((mInitFeedbackData == 0) && (mShiftRegisterInput == 0)))
	{
	    mAdditionOutput = 0;
	}
	else
	{
	    mAdditionOutput = 1;    
	} 
	output.pushInt(mInitFeedbackData);
	output.pushInt(mAdditionOutput);
    }

}//AdditionFilter

class InitialAdditionFilter extends Filter

{
    //inputs:
    int mInitFeedbackData;
    int mFileInput;
    //outputs
    //output 1: mInitFeedbackData
    int mAdditionOutput;


    public void init()
    {
	input = new Channel(Integer.TYPE, 2);
	output = new Channel(Integer.TYPE, 2);
    }

    public void work()
    {
	mFileInput = input.popInt();
	mInitFeedbackData = input.popInt();
	System.err.println("File Input is " + mFileInput + " Feedback data is " + mInitFeedbackData);
	if(((mInitFeedbackData == 1) && (mFileInput == 1)) || ((mInitFeedbackData == 0) && (mFileInput == 0)))
	{
	    mAdditionOutput = 0;
	}
	else
	{
	    mAdditionOutput = 1;    
	} 
	output.pushInt(mAdditionOutput);
	output.pushInt(mAdditionOutput);
    }

}//InitialAdditionFilter

class ZeroAdditionFilter extends Filter
{
    //inputs:
    int mInitFeedbackData;
    int mShiftRegisterInput;
    //outputs
    //output 1: mInitFeedbackData
    int mAdditionOutput;


    public void init()
    {
	input = new Channel(Integer.TYPE, 2);
	output = new Channel(Integer.TYPE, 2);
    }

    public void work()
    {
	mInitFeedbackData = input.popInt();
	mShiftRegisterInput = input.popInt();
	
	 
	output.pushInt(mInitFeedbackData);
	output.pushInt(mShiftRegisterInput);
    }

}//ZeroAdditionFilter  //i.e. the identity filter
    
class ShiftRegisterFilter extends Filter
{
    
    //private register variable:
    int mRegisterNumber;
    int mRegisterContents;
    //inputs:
    int mInitFeedbackData;
    int mDataStream;
    //output:
    int mRegisterOutput;
    int mTerminationCounter;
    
    public ShiftRegisterFilter(int number)
    {
	mRegisterNumber = number;
    }
    public void init()
    {
	mTerminationCounter = 0;
	input = new Channel(Integer.TYPE, 2);
	output = new Channel(Integer.TYPE, 2);
	mRegisterContents = 0; //initial value
    }

    public void work()
    {	
	mInitFeedbackData = input.popInt();
	mDataStream = input.popInt();
	mRegisterOutput = mRegisterContents;   //move datastream value into Shift Register
	mRegisterContents = mDataStream;       //move original contents to output
	mTerminationCounter++;
	if(mTerminationCounter < EncoderInput.mInputLength + 1)
	{
	    System.err.println("Shift Register Contents of Filter " + mRegisterNumber + " at iteration " + mTerminationCounter + " is " + mRegisterContents);
	    System.err.println("Shift Register Output of Filter " + mRegisterNumber + " at iteration " + mTerminationCounter + " is " + mRegisterOutput);
	    
	}
	output.pushInt(mInitFeedbackData);
	output.pushInt(mRegisterOutput);
    }
}//ShiftRegisterFilter

class CrcInputFilter extends Filter
{
    int[] mdata;
    EncoderInput mencodefile;

    public void init()
    {
	mencodefile = new EncoderInput();
	mdata = new int[mencodefile.mInputLength];
	output = new Channel(Integer.TYPE, 1);
    }

    public void work()
    {
	mdata = mencodefile.readFile();
	for (int i = 0; i < mencodefile.mInputLength; i++)
	{
	    //System.err.println("inputting " + mdata[i]);
	    output.pushInt(mdata[i]);
	}
    }

}//CrcInputFilter

class FeedbackEndFilter extends Filter
{
    int mRegisterOutput;
    int mInitFeedbackData;

    public void init()
    {
	input = new Channel(Integer.TYPE, 2);
	output = new Channel(Integer.TYPE, 1);
    }
    
    public void work()
    {
	mInitFeedbackData = input.popInt();
	mRegisterOutput = input.popInt();
	System.err.println("Shift Register 3 is " + mRegisterOutput);
	output.pushInt(mRegisterOutput);
    }
}//FeedbackEndFilter

class CrcFeedbackLoop extends FeedbackLoop
{
    public void init()
    {
	this.setDelay(1);
	this.setJoiner(WEIGHTED_ROUND_ROBIN (1, 1));  //sequence: inputdata, fbdata
	this.setBody(new FeedbackBodyStream());
	this.setSplitter(DUPLICATE ());
	this.setLoop(new Identity(Integer.TYPE));
    }

    public int initPathInt(int index)
    {
	return 0;
    }
}//CrcFeedbackLoop

class FeedbackBodyStream extends Pipeline
{
    public void init()
    {
	this.add(new InitialAdditionFilter());
	this.add(new ShiftRegisterFilter(1));
	this.add(new AdditionFilter());
	this.add(new ShiftRegisterFilter(2));
	this.add(new Identity(Integer.TYPE));
	//this.add(new ShiftRegisterFilter(3));
	this.add(new FeedbackEndFilter());
    }
   
}//FeedbackBodyStream

public class CrcEncoderTest extends StreamIt
{
    public static void main(String args[])
    {
	new CrcEncoderTest().run(args);
    }

    public void init()
    {
	this.add(new CrcInputFilter());
	this.add(new CrcFeedbackLoop());
	this.add(new streamit.io.FileWriter("bleh", Integer.TYPE));
    }
}//class CrcEncoder





