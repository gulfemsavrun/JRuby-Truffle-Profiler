# Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

create_reference = false
ruby = false
ruby_profiler = false
jruby = false
jruby_profiler = false
simple_truffle = false
number_of_runs = 1
profile_calls = false
profile_calls_builtins = false
profile_control_flow = false
profile_variable_accesses = false
profile_operations = false
profile_collection_operations = false
profile_type_distribution = false
profile_sort = false
profile_sort_flag = ""
calculate_overhead = false

args = ARGV.dup

while not args.empty?
  arg = args.shift

  case arg
  when "-create-reference"
    create_reference = true
  when "-ruby"
    puts "RUNNING RUBY"
    ruby = true
  when "-ruby-profiler"
    puts "RUNNING RUBY PROFILER"
    ruby_profiler = true
  when "-jruby"
    puts "RUNNING JRUBY"
    jruby = true   
  when "-jruby-profiler"
    puts "RUNNING JRUBY PROFILER"
    jruby_profiler = true
  when "-number-of-runs"
    number_of_runs = args.shift.to_i
  when "-simple-truffle"
    puts "RUNNING SIMPLE TRUFFLE"
    simple_truffle = true
  when "-profile-calls"
    puts "PROFILING CALLS"
    profile_calls = true
  when "-profile-calls-builtins"
    puts "PROFILING CALLS AND BUILTINS"
    profile_calls_builtins = true
  when "-profile-control-flow"
    puts "PROFILING CONTROL FLOW"
    profile_control_flow = true
  when "-profile-variable-accesses"
    puts "PROFILING VARIABLE ACCESSES"
    profile_variable_accesses = true
  when "-profile-operations"
    puts "PROFILING OPERATIONS"
    profile_operations = true
  when "-profile-collection-operations"
    puts "PROFILING COLLECTION OPERATIONS"
    profile_collection_operations = true
  when "-profile-type-distribution"
    puts "PROFILING TYPE DISTRIBUTION"
    profile_type_distribution = true
  when "-profile-sort"
    profile_sort = true
  when "-calculate-overhead"
    calculate_overhead = true  
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

reference_scores = {}

file = nil
reference_file = nil
overhead_file = nil
overhead_time_file = nil

if create_reference
  reference_file = File.open("benchmark.reference", "w")
  reference_file.write("-number-of-runs #{number_of_runs}\n")
elsif calculate_overhead
  reference_file = File.open("benchmark.reference")
  reference_file.each do |line|
    key, value = line.split
    if key == "-number-of-runs"
      number_of_runs_reference = value.to_i
    else
      reference_scores[key] = value.to_f
    end
  end

  overhead_file = File.open("benchmark.overhead",  "w")
  overhead_file.write("-number-of-runs #{number_of_runs}\n")
  overhead_difference_file = File.open("benchmark.overhead_difference",  "w")
  overhead_difference_file.write("-number-of-runs #{number_of_runs}\n")
end

file = File.open("benchmark.results", "w")
file.write("-number-of-runs #{number_of_runs}\n")


if ENV["JAVACMD"].nil? or not File.exist? File.expand_path(ENV["JAVACMD"])
  puts "warning: couldn't find $JAVACMD - set this to the path of the Java command in graalvm-jdk1.8.0 or a build of the Graal repo"
end

benchmarks = [
  "binary-trees-z",
  "fannkuch-redux-z",
  "mandelbrot-z",
  "n-body-z",
  "pidigits-z",
  "richards-z",
  "spectral-norm-z"
]

scores = {}

folder_name = "benchmarks_zippy"

benchmarks.each do |benchmark|
  benchmark_path = folder_name + "/" + benchmark
  total_score = 0
  total_overhead = 0
  total_difference = 0

  if number_of_runs > 0
    for run in 1..number_of_runs
      puts "run " + run.to_s
      puts "running " + benchmark

      if ruby
        output = `ruby #{benchmark_path}.rb`
      elsif ruby_profiler
        #output = `ruby -rprofile #{benchmark_path}.rb`
        #output = `ruby-prof #{benchmark_path}.rb --sort=total`
        output = `ruby-prof --exclude "Bignum|Fixnum|Float|Integer|Numeric|Rational|String|Array|BasicObject|Enumerable|Range|Kernel|Module#." --sort=total #{benchmark_path}.rb`
        # Use puts to print the output of the ruby-prof profiler
        puts output
      elsif jruby
        output = `../../bin/jruby -Xcompile.mode=JIT -Xcompile.invokedynamic=true #{benchmark_path}.rb`
      elsif jruby_profiler
        output = `../../bin/jruby -Xcompile.mode=JIT -Xcompile.invokedynamic=true --profile #{benchmark_path}.rb`
      elsif simple_truffle
        output = `../../bin/jruby -J-server -J-Xmx2G -X+T #{benchmark_path}.rb`
      else
        if profile_sort
          profile_sort_flag = "-Xtruffle.profile.sort=true"
        end

        if profile_calls
          output = `../../bin/jruby -J-server -J-Xmx2G -X+T -Xtruffle.profile.calls=true #{profile_sort_flag} #{benchmark_path}.rb`
        end
        if profile_calls_builtins
          output = `../../bin/jruby -J-server -J-Xmx2G -X+T -Xtruffle.profile.calls=true -Xtruffle.profile.builtin_calls=true #{profile_sort_flag} #{benchmark_path}.rb`
        end
        if profile_control_flow
          output = `../../bin/jruby -J-server -J-Xmx2G -X+T -Xtruffle.profile.control_flow=true #{profile_sort_flag} #{benchmark_path}.rb`
        end
        if profile_variable_accesses
          if profile_type_distribution
            output = `../../bin/jruby -J-server -J-Xmx2G -X+T -Xtruffle.profile.variable_accesses=true -Xtruffle.profile.type_distribution=true #{profile_sort_flag} #{benchmark_path}.rb`
          else
            output = `../../bin/jruby -J-server -J-Xmx2G -X+T -Xtruffle.profile.variable_accesses=true #{profile_sort_flag} #{benchmark_path}.rb`
          end
        end
        if profile_operations
          output = `../../bin/jruby -J-server -J-Xmx2G -X+T -Xtruffle.profile.operations=true #{profile_sort_flag} #{benchmark_path}.rb`
        end
        if profile_collection_operations
          output = `../../bin/jruby -J-server -J-Xmx2G -X+T -Xtruffle.profile.collection_operations=true #{profile_sort_flag} #{benchmark_path}.rb`
        end
      end

      score_match = /[a-z\-]+: (\d+\.\d+)/.match(output)

      if score_match.nil?
        score = 0
        puts benchmark + " error"
        puts output
      else
        score = score_match[1].to_f
        puts benchmark + ": " + score.round(2).to_s
        
        if calculate_overhead 
          puts "reference: " + reference_scores[benchmark].to_s
          difference = score - reference_scores[benchmark]
          overhead_percentage = difference / reference_scores[benchmark] * 100
          puts "#{benchmark} overhead: #{overhead_percentage.round(2)}%"
          puts "#{benchmark} difference: #{difference.round(2)}"
          overhead_file.write("#{benchmark} #{overhead_percentage.round(2)}\n")
          overhead_difference_file.write("#{benchmark} #{difference.round(2)}\n")

          if (overhead_percentage > 0)
            total_overhead = total_overhead + overhead_percentage
            total_difference = total_difference + difference
          end
        else
          if (create_reference and number_of_runs == 1)
            reference_file.write("#{benchmark} #{score.round(2)}\n")
          end
        end

        file.write("#{benchmark} #{score.round(2)}\n")
        total_score = total_score + score
      end
    end

    if (number_of_runs > 1)
      avg_score = total_score / number_of_runs
      puts benchmark + " avg: " + avg_score.round(2).to_s

      if create_reference
        reference_file.write("#{benchmark} #{avg_score.round(2)}\n")
      end

      if calculate_overhead
        avg_overhead_percentage = total_overhead / number_of_runs
        avg_difference = total_difference / number_of_runs
        puts benchmark + " avg overhead: " + avg_overhead_percentage.round(2).to_s + "%"
        overhead_file.write("avg: #{benchmark} #{avg_overhead_percentage.round(2)}\n\n")
        puts benchmark + " avg difference: " + avg_difference.round(2).to_s
        overhead_difference_file.write("avg: #{benchmark} #{avg_difference.round(2)}\n\n")
      end

      file.write("avg: #{benchmark} #{avg_score.round(2)}\n\n")
    end
  end
end

