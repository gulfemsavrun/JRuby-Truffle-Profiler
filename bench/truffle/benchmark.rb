# Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

reference = false
jruby = false
profiler_jruby = false
simple_truffle = false
number_of_runs = 1
profile_calls = false
profile_control_flow = false
profile_variable_accesses = false
profile_operations = false
profile_attributes_elements = false
profile_type_distribution = false
profile_sort = false
profile_sort_flag = ""

args = ARGV.dup

while not args.empty?
  arg = args.shift

  case arg
  when "-jruby"
    puts "RUNNING SIMPLE JRUBY"
    jruby = true   
  when "-profiler-jruby"
    puts "RUNNING JRUBY PROFILER"
    profiler_jruby = true     
  when "-number-of-runs"
    number_of_runs = args.shift.to_i
  when "-simple-truffle"
    puts "RUNNING SIMPLE TRUFFLE"
    simple_truffle = true
  when "-profile-calls"
    puts "PROFILING CALLS"
    profile_calls = true
  when "-profile-control-flow"
    puts "PROFILING CONTROL FLOW"
    profile_control_flow = true
  when "-profile-variable-accesses"
    puts "PROFILING VARIABLE ACCESSES"
    profile_variable_accesses = true
  when "-profile-operations"
    puts "PROFILING OPERATIONS"
    profile_operations = true
  when "-profile-attributes-elements"
    puts "PROFILING ATTRIBUTES ELEMENTS"
    profile_attributes_elements = true
  when "-profile-type-distribution"
    puts "PROFILING TYPE DISTRIBUTION"
    profile_type_distribution = true
  when "-profile-sort"
    profile_sort = true
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

if ENV["JAVACMD"].nil? or not File.exist? File.expand_path(ENV["JAVACMD"])
  puts "warning: couldn't find $JAVACMD - set this to the path of the Java command in graalvm-jdk1.8.0 or a build of the Graal repo"
end

benchmarks = [
#  "binary-trees-z",
#  "fannkuch-redux-z",
  "mandelbrot-z"#,
#  "n-body-z"#,
#  "pidigits-z",
#  "spectral-norm-z",
#  "richards-z",
]

disable_splitting = [
  "spectral-norm"
]

scores = {}

folder_name = "benchmarks_zippy"

File.open("benchmark.results", "w") do |file|
  benchmarks.each do |benchmark|
    benchmark_path = folder_name + "/" + benchmark
    total_score = 0

    if number_of_runs > 0
      for run in 1..number_of_runs
        puts "run " + run.to_s
        puts "running " + benchmark

        if disable_splitting.include? benchmark
            splitting = "-J-G:-TruffleSplitting"
          else
            splitting = ""
          end

          if jruby
            output = `../../bin/jruby -J-server -J-Xmx2G #{benchmark_path}.rb`
          elsif profiler_jruby
            output = `../../bin/jruby -J-server -J-Xmx2G --profile #{benchmark_path}.rb`
          elsif simple_truffle
            output = `../../bin/jruby -J-server -J-Xmx2G #{splitting} -X+T #{benchmark_path}.rb`
          else
            if profile_sort
              profile_sort_flag = "-Xtruffle.profile.sort=true"
            end

            if profile_calls
              output = `../../bin/jruby -J-server -J-Xmx2G #{splitting} -X+T -Xtruffle.profile.calls=true #{profile_sort_flag} #{benchmark_path}.rb`
            end
            if profile_control_flow
              output = `../../bin/jruby -J-server -J-Xmx2G #{splitting} -X+T -Xtruffle.profile.control_flow=true #{profile_sort_flag} #{benchmark_path}.rb`
            end
            if profile_variable_accesses
              output = `../../bin/jruby -J-server -J-Xmx2G #{splitting} -X+T -Xtruffle.profile.variable_accesses=true #{profile_sort_flag} #{benchmark_path}.rb`
            end
            if profile_operations
              output = `../../bin/jruby -J-server -J-Xmx2G #{splitting} -X+T -Xtruffle.profile.operations=true #{profile_sort_flag} #{benchmark_path}.rb`
            end
            if profile_attributes_elements
              output = `../../bin/jruby -J-server -J-Xmx2G #{splitting} -X+T -Xtruffle.profile.attributes_elements=true #{profile_sort_flag} #{benchmark_path}.rb`
            end
            if profile_type_distribution
              output = `../../bin/jruby -J-server -J-Xmx2G #{splitting} -X+T -Xtruffle.profile.sort=true #{profile_sort_flag} #{benchmark_path}.rb`
            end
          end

          score_match = /[a-z\-]+: (\d+\.\d+)/.match(output)

          if score_match.nil?
            score = 0
            puts benchmark + " error"
            puts output
          else
            score = score_match[1].to_f
            puts benchmark + " " + score.to_s
            file.write("#{benchmark} #{score}\n")
            total_score = total_score + score
          end
        end

        if (number_of_runs > 1)
          avg_score = total_score / number_of_runs
          puts benchmark + " avg " + avg_score.to_s
          file.write("#{benchmark} avg #{score}\n\n")
          scores[benchmark] = avg_score
        end
    end
  end
end

