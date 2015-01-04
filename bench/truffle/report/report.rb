# Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

time_budget = 60
reports = []

args = ARGV.dup

while not args.empty?
  arg = args.shift

  if arg.start_with? "-"
    case arg
    when "-s"
      time_budget = args.shift.to_i
    when "-m"
      time_budget = args.shift.to_i * 60
    when "-h"
      time_budget = args.shift.to_i * 60 * 60
    when "--help", "-help", "-h"
      puts "JRUBY_DIR=... JRUBY_TRUFFLE_HEAD_DIR=... GRAAL_RELEASE_DIR=... GRAAL_HEAD_DIR=... ruby report.rb options reports..."
      puts
      puts "  -s n  run for n seconds (default 60)"
      puts "  -m n  run for n minutes"
      puts "  -h n  run for n hours"
      puts
      puts "  report: all almost-all competition interpreters jruby jruby-master summary topaz other-vms"
      puts
      puts "  Runs gnuplot, but you can also copy and paste the data files into Excel"
      exit
    else
      puts "unknown argument " + arg
      exit
    end
  else
    if ["rbx", "topaz", "jruby", "competition", "almost-all", "all", "interpreters", "summary", "other-vms"].include? arg
      reports.push(arg)
    else
      puts "unknown report " + arg
      exit
    end
  end
end

if reports.empty?
  reports.push("all")
end

report_references = {
  "topaz" => "topaz-dev",
  "rbx" => "rbx-2.2.9",
  "jruby" => "jruby-1.7.13-server-indy",
  "competition" => "jruby-1.7.13-server-indy",
  "almost-all" => "2.1.2",
  "all" => "1.8.7-p375",
  "interpreters" => "2.1.2",
  "summary" => "2.1.2",
  "other-vms" => "2.1.2",
}

Ruby = Struct.new(
  :name,
  :command,
  :relevant_reports
)

rubies = []

["1.8.7-p375", "1.9.3-p547", "2.0.0-p481", "2.1.2", "ree-1.8.7-2012.02", "rbx-2.2.9"].each do |name|
  dir = "~/.rbenv/versions/" + name

  if Dir.exists? File.expand_path(dir)
    relevant_reports = ["all"]

    if ["1.8.7-p375", "1.9.3-p547", "2.0.0-p481", "2.1.2", "ree-1.8.7-2012.02"].include? name
      relevant_reports.push("interpreters")
    end

    if ["2.1.2", "rbx-2.2.9"].include? name
      relevant_reports.push("almost-all")
    end

    if name == "rbx-2.2.9"
      relevant_reports.push("summary")
      relevant_reports.push("competition")
      relevant_reports.push("other-vms")
      relevant_reports.push("rbx")
      rubies.push Ruby.new(name + "-interpreter", dir + "/bin/ruby -Xint", ["all", "interpreters"])
    end

    if name == "2.1.2"
      relevant_reports.push("summary")
      relevant_reports.push("other-vms")
    end

    rubies.push Ruby.new(name, dir + "/bin/ruby", relevant_reports)
  else
    puts "warning: couldn't find " + dir
  end
end

["jruby-1.7.13"].each do |name|
  dir = "~/.rbenv/versions/" + name

  if Dir.exists? File.expand_path(dir)
    rubies.push Ruby.new(name + "-server-interpreter", dir + "/bin/jruby --server -Xcompile.mode=OFF", ["almost-all", "all", "jruby", "interpreters"])
    rubies.push Ruby.new(name + "-server", dir + "/bin/jruby --server", ["almost-all", "all", "jruby"])
    rubies.push Ruby.new(name + "-server-indy", dir + "/bin/jruby --server -Xcompile.invokedynamic=true", ["almost-all", "all", "competition", "jruby", "summary", "other-vms"])
  else
    puts "warning: couldn't find " + dir
  end
end

if Dir.exists? File.expand_path("~/.rbenv/versions/topaz-dev")
  rubies.push Ruby.new("topaz-dev", "~/.rbenv/versions/topaz-dev/bin/ruby", ["almost-all", "all", "competition", "topaz", "summary", "other-vms"])
else
  puts "warning: couldn't find ~/.rbenv/versions/topaz-dev"
end

if not ENV["JRUBY_DIR"].nil? and not ENV["GRAAL_RELEASE_DIR"].nil? and Dir.exists? File.expand_path(ENV["JRUBY_DIR"]) and Dir.exists? File.expand_path(ENV["GRAAL_RELEASE_DIR"])
  rubies.push Ruby.new("jruby-master-server-ir-interpreter", "$JRUBY_DIR/bin/jruby --server -X-CIR", ["all", "jruby", "interpreters"])
  rubies.push Ruby.new("jruby-master-server-ir-compiler", "$JRUBY_DIR/bin/jruby --server -X+CIR", ["all", "jruby"])
  rubies.push Ruby.new("jruby-master+truffle-server-original", "JAVACMD=$GRAAL_RELEASE_DIR/bin/java $JRUBY_DIR/bin/jruby -J-original -X+T -Xtruffle.printRuntime=true", ["all", "interpreters", "jruby"])
  rubies.push Ruby.new("jruby-master+truffle-server", "JAVACMD=$GRAAL_RELEASE_DIR/bin/java $JRUBY_DIR/bin/jruby -J-server -X+T -Xtruffle.printRuntime=true", ["almost-all", "all", "jruby", "topaz", "rbx", "competition", "summary", "other-vms"])
else
  puts "warning: couldn't find $JRUBY_DIR or $GRAAL_RELEASE_DIR"
end

benchmarks = [
  "binary-trees",
  "fannkuch-redux",
  "mandelbrot",
  "n-body",
  "pidigits",
  "spectral-norm",
  "neural-net",
  "richards",
  "deltablue"
]

disable_splitting = [
  "spectral-norm",
  "neural-net"
]

if reports == ["other-vms"]
  benchmarks = ["mandelbrot"]
end

rubies_to_run = rubies.select do |ruby|
  not (ruby.relevant_reports & reports).empty?
end

time_budget_per_run = time_budget / benchmarks.length / rubies_to_run.length
puts time_budget_per_run.to_s + "s for each combination of benchmark and implementation"

if time_budget_per_run < 10
  puts "WARNING: time to run each benchmark is very low - increase the budget"
end

scores = {}

benchmarks.each do |benchmark|
  scores[benchmark] = {}

  rubies_to_run.each do |ruby|
    if disable_splitting.include? benchmark and ruby.command.include? "-J-server"
      splitting = "-J-G:-TruffleSplittingEnabled"
    else
      splitting = ""
    end

    output = `#{ruby.command} #{splitting} $JRUBY_DIR/bench/truffle/harness.rb -s #{time_budget_per_run} $JRUBY_DIR/bench/truffle/#{benchmark}.rb`
    score_match = /[a-z\-]+: (\d+\.\d+)/.match(output)
    if score_match.nil?
      score = 0
      puts benchmark + " " + ruby.name + " error"
      puts output
    else
      score = score_match[1].to_f
      puts benchmark + " " + ruby.name + " " + score.to_s
    end
    scores[benchmark][ruby.name] = score
  end
end

if reports.include? "other-vms"
  `echo budget = #{time_budget_per_run} > $JRUBY_DIR/bench/truffle/js/budget.js`
  output = `v8 $JRUBY_DIR/bench/truffle/js/budget.js $JRUBY_DIR/bench/truffle/js/mandelbrot.js`
  score_match = /(\d+\.\d+)/.match(output)
  if score_match.nil?
    score = 0
    puts "mandelbrot v8 error"
    puts output
  else
    score = score_match[1].to_f
    puts "mandelbrot v8 #{score}"
  end
  scores["mandelbrot"]["v8"] = score

  if not File.exist?(ENV["JRUBY_DIR"] + "/bench/truffle/java/Mandelbrot.class")
    puts "warning: you need to build $JRUBY_DIR/bench/truffle/java/Mandelbrot.class"
  end

  output = `java -classpath $JRUBY_DIR/bench/truffle/java Mandelbrot #{time_budget_per_run}`
  score_match = /(\d+\.\d+)/.match(output)
  if score_match.nil?
    score = 0
    puts "mandelbrot java error"
    puts output
  else
    score = score_match[1].to_f
    puts "mandelbrot java #{score}"
  end
  scores["mandelbrot"]["java"] = score

  if not File.exist?(ENV["JRUBY_DIR"] + "/bench/truffle/c/mandelbrot")
    puts "warning: you need to build $JRUBY_DIR/bench/truffle/c/mandelbrot"
  end

  output = `$JRUBY_DIR/bench/truffle/c/mandelbrot #{time_budget_per_run}`
  score_match = /(\d+\.\d+)/.match(output)
  if score_match.nil?
    score = 0
    puts "mandelbrot c error"
    puts output
  else
    score = score_match[1].to_f
    puts "mandelbrot c #{score}"
  end
  scores["mandelbrot"]["c"] = score
end

def relative_to(benchmarks, scores, reference)
  relative_scores = {}

  benchmarks.each do |benchmark|
    relative_scores[benchmark] = {}
    reference_score = scores[benchmark][reference]

    if reference_score == 0
      puts "warning: reference score for #{benchmark} using #{reference} was zero"
    end

    scores[benchmark].each do |ruby, score|
      if reference_score == 0
        relative_scores[benchmark][ruby] = 0
      else
        relative_scores[benchmark][ruby] = score / reference_score
      end
    end
  end

  relative_scores
end

def plot(rubies_to_run, report, reference, benchmarks, scores)
  sores_relative_reference = relative_to(benchmarks, scores, reference)

  File.open("#{report}.data", "w") do |file|
    header = ["Implementation"] + benchmarks
    file.write(header.join(" ") + "\n")

    rubies_to_run.each do |ruby|
      if ruby.relevant_reports.include? report
        line = [ruby.name]

        benchmarks.each do |benchmark|
          line.push(sores_relative_reference[benchmark][ruby.name])
        end

        file.write(line.join(" ") + "\n")
      end
    end

    if report == "other-vms"
      file.write("v8 #{sores_relative_reference["mandelbrot"]["v8"]}\n")
      file.write("java #{sores_relative_reference["mandelbrot"]["java"]}\n")
      file.write("c #{sores_relative_reference["mandelbrot"]["c"]}\n")
    end
  end

  puts "gnuplot #{report}.gnuplot"
  `gnuplot #{report}.gnuplot`
end

reports.each do |report|
  plot(rubies_to_run, report, report_references[report], benchmarks, scores)
end
