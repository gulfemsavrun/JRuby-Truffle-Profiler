# Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

time_budget = nil
reference = false
verbose = false
jruby = false
profiler_jruby = false
number_of_runs = 1

args = ARGV.dup

while not args.empty?
  arg = args.shift

  case arg
  when "--reference"
    reference = true
  when "-jruby"
    jruby = true   
  when "-profiler-jruby"
    profiler_jruby = true     
  when "-number-of-runs"
    number_of_runs = args.shift.to_i        
  when "-s"
    time_budget = args.shift.to_i
  when "-m"
    time_budget = args.shift.to_i * 60
  when "-h"
    time_budget = args.shift.to_i * 60 * 60
  when "-v"
    verbose = true
  when "--help", "-help", "-h"
    puts "Note: use a system Ruby to run this script, not the development JRuby, as that seems to sometimes get in a twist"
    puts
    puts "Set a reference point, running for 5 minutes:"
    puts "    JAVACMD=... ruby benchmark-compare.rb --reference -m 5"
    puts
    puts "Compare against that reference point:"
    puts "    JAVACMD=... ruby benchmark-compare.rb"
    puts
    puts "  -s n  run for n seconds (default 60)"
    puts "  -m n  run for n minutes"
    puts "  -h n  run for n hours"
    puts
    puts "  -v    show all output"
    exit
  else
    puts "unknown argument " + arg
    exit
  end
end

if reference
  if time_budget.nil?
    time_budget = 60
  end
else
  reference_scores = {}

  if not time_budget.nil?
    puts "can't specify budget unless --reference"
    exit
  end

  File.open("reference.data").each do |line|
    key, value = line.split
    if key == "-s"
      time_budget = value.to_i
    else
      reference_scores[key] = value.to_f
    end
  end
end

if ENV["JAVACMD"].nil? or not File.exist? File.expand_path(ENV["JAVACMD"])
  puts "warning: couldn't find $JAVACMD - set this to the path of the Java command in graalvm-jdk1.8.0 or a build of the Graal repo"
end

benchmarks = [
  "benchmarks_zippy/binary-trees-z",
  "benchmarks_zippy/fannkuch-redux-z",
  "benchmarks_zippy/mandelbrot-z",
  "benchmarks_zippy/n-body-z",
  "benchmarks_zippy/pidigits-z",
  "benchmarks_zippy/spectral-norm-z",
  "benchmarks_zippy/richards-z",
]

disable_splitting = [
  "spectral-norm",
  "neural-net"
]

time_budget_per_run = time_budget / benchmarks.length

puts time_budget_per_run.to_s + "s for each benchmark" if reference

scores = {}
      
benchmarks.each do |benchmark|
  total_score = 0
  if number_of_runs > 0
    for run in 1..number_of_runs
      puts "running " + run.to_s 
      if disable_splitting.include? benchmark
        splitting = "-J-G:-TruffleSplitting"
      else
        splitting = ""
      end
    
      if jruby
        #output = `../../bin/jruby -J-server harness.rb -s #{time_budget_per_run} #{benchmark}.rb`
        output = `../../bin/jruby -J-server -J-Xmx2G #{benchmark}.rb`
      else 
        if profiler_jruby
          #output = `../../bin/jruby -J-server --profile harness.rb -s #{time_budget_per_run} #{benchmark}.rb`
          output = `../../bin/jruby -J-server -J-Xmx2G --profile #{benchmark}.rb`
        else
          #output = `../../bin/jruby -J-server #{splitting} -X+T  harness.rb -s #{time_budget_per_run} #{benchmark}.rb`
          output = `../../bin/jruby -J-server -J-Xmx2G #{splitting} -X+T #{benchmark}.rb`
        end
      end
      
      score_match = /[a-z\-]+: (\d+\.\d+)/.match(output)

      if score_match.nil?
        score = 0
        puts benchmark + " error"
        puts output
      else
        if verbose
          puts output
        end
        
        score = score_match[1].to_f
        #puts benchmark + " " + score.to_s
        total_score = total_score + score
      end
    end  
  end
  #scores[benchmark] = score
  avg_score = total_score / number_of_runs
  puts benchmark + " " + avg_score.to_s
  scores[benchmark] = avg_score
end


if reference
  File.open("reference.data", "w") do |file|
    file.write("-s #{time_budget}\n")

    benchmarks.each do |benchmark|
      file.write("#{benchmark} #{scores[benchmark]}\n")
    end
  end
else
  puts "-------"
  benchmarks.each do |benchmark|
    increase = scores[benchmark] - reference_scores[benchmark]
    increase_percentage = increase / reference_scores[benchmark] * 100
    puts "#{benchmark.ljust(15)} #{increase_percentage.round(2).to_s.rjust(6)}%"
  end
end
