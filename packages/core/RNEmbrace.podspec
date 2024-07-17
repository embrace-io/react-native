require "json"
package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "RNEmbrace"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.ios.deployment_target = '9.0'
  s.tvos.deployment_target = '10.0'

  s.source_files = 'ios/RNEmbrace/*.{h,m}'
  s.source = {:path => "ios/RNEmbrace/"}

  s.dependency 'React-Core'
  s.dependency 'EmbraceIO-DEV', '6.2.0'
end
