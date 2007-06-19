#!/usr/uns/bin/perl
# Script to parse the output of "make run" from the
# RAW btl simulator and compare it against
# an expected output file. The first command line argument
# is raw output, and second argument is expected output. 
#
# Returns with exit status 0 if the raw output and the expected output
# match up to the end of the shortest one (this is necessary because
# running a raw program an exact number of steady state cycles is hard (because
# all you seem to be able to specify is the number of simulation cycles.)
#
# Prints out the cycle of the last btl cycle that produced output that was
# checked against expected results followed by a space followed by the 
# last line number of the expected results that was used for a comparison. 
#
# Example input (output from btl simulator):
# [72: 000000fcf]: 243.000000
# [72: 000000ffa]: -81.000000
# [72: 000001025]: -81.000000
# [72: 00000108b]: 27.000000
#
# Also note: We compare the values produced by btl against the expected output
# (most often generated by running the program in java)
# by value, not signaling an error if the output is within 
# $TOLERANCE percent of the expected output. See comparelib.pl
#
# Usage: compare_raw.pl raw_output.txt expected_output.txt
#
# AAL 6/26/2002
#     7/22/2002
# $Id: compare_raw.pl,v 1.4 2007-06-19 06:27:19 thies Exp $
#######

use strict;
use lib ($ENV{'STREAMIT_HOME'} . '/regtest/tools');
require "comparelib.pl";

# kick it back to the old skool c style of doing things with a main()
# function...
main();





## 
# Main Entry Point 
##
sub main {
    #read in the command line arguments
    my $raw_filename      = shift(@ARGV) || die (get_usage());
    my $expected_filename = shift(@ARGV) || die (get_usage());
    
    # read in the file contents -- raw file and expected file
    my $raw_contents      = read_file($raw_filename);
    my $expected_contents = read_file($expected_filename);

    # split up the expected value based on newlines (so we can compare the output line by line);
    my @expected_items = split(/\n/, $expected_contents);

    # now, run a regexp pattern match on raw results
    # to extract the actual output from the simulator
    my @items = $raw_contents =~ m/\[(.+\:)\s(.+)]\:\s(.*)\n/g;
   
    # keep track of the current line number
    my $current_line = 1; 
    # keep track of the current pc
    my $current_pc;

    # while we still have both raw items and expected items to compare
    while (@items and @expected_items) {
	my $raw_number  = shift(@items); # I think it is a line number of the printf
	   $current_pc  = shift(@items); # pc where the output occured
	my $raw_output  = shift(@items); # what the program actually outputs
	
	my $expected_output = shift(@expected_items);
	#print ("line: $current_line, pc: $current_pc\n");
	#print ("output: $raw_output\nexpected: $expected_output\n");


	# compare the value with the comparelib routine
	# this will exit with an error message if the value is not within tolerance.
	compare_values_relative($current_line, $expected_output, $raw_output);
	
	# update current line
	$current_line++;
    }

    # if we get here, it means that the two files were identical up to the last element
    # in the shortest file.
    $current_line--;
    print("$current_pc $current_line\n");
    exit(0); # all done
}







################################
# Subroutines
################################


# get a usage message
sub get_usage {
    return "usage: compare_raw.pl raw_output.txt expected_output.txt\n";
}

