import React
import JavaScriptCore
import Foundation
import EmbraceIO
import EmbraceCrash
import OpenTelemetrySdk
import EmbraceCommonInternal

class SDKConfig: NSObject {
    public let appId: String
    public let appGroupId: String?
    public let disableCrashReporter: Bool
    public let disableAutomaticViewCapture: Bool
    public let endpointBaseUrl: String?

    public init(from: NSDictionary) {
        self.appId = from["appId"] as? String ?? ""
        self.appGroupId = from["appGroupId"] as? String
        self.disableCrashReporter = from["disableCrashReporter"] as? Bool ?? false
        self.disableAutomaticViewCapture = from["disableAutomaticViewCapture"] as? Bool ?? false
        self.endpointBaseUrl = from["endpointBaseUrl"] as? String
    }
}

@objc class CustomExporterConfig: NSObject {
    var endpoint: String
    var timeout: NSNumber?
    var header: [(String, String)]?

    init(endpoint: String, timeout: NSNumber?, header: [(String, String)]?) {
        self.endpoint = endpoint
        self.timeout = timeout
        self.header = header
    }

    // Helper method to create `CustomExporterConfig` from NSDictionary (JS object)
    @objc static func fromDictionary(_ dict: NSDictionary) -> CustomExporterConfig {
        let endpoint = dict["endpoint"] as! String
        let timeout = dict["timeout"] as? NSNumber

        var headers: [(String, String)] = []
        if let headerDicts = dict["header"] as? [NSDictionary] {
            for headerDict in headerDicts {
                let tuples: [(String, String)] = headerDict.allKeys.compactMap { key in
                    guard let keyString = key as? String,
                          let value = headerDict[key] as? String else {
                        return nil
                    }
                    return (keyString, value)
                }
                
                headers.append(contentsOf: tuples)
            }
        }

        return CustomExporterConfig(endpoint: endpoint, timeout: timeout, header: headers)
    }
}

func convertToTimeInterval(from number: NSNumber?) -> TimeInterval? {
    guard let number = number else {
        return nil
    }
    return number.doubleValue
}

@objc(RNEmbraceOTLP)
class RNEmbraceOTLP: NSObject {
  // Http starts
  private func setOtlpHttpTraceExporter(endpoint: String,
                                        timeout: NSNumber,
                                        header: [(String,String)]?) -> OtlpHttpTraceExporter {
    
    return OtlpHttpTraceExporter(endpoint: URL(string: endpoint)!, // NOTE: make sure about extra validations (format/non-empty)
                                 config: OtlpConfiguration(
                                    timeout: convertToTimeInterval(from: timeout)!, // NOTE: make sure about extra validations (not-nil)
                                    headers: header
                                 )
    );
  }
  
  private func setOtlpHttpLogExporter(endpoint: String,
                                      timeout: NSNumber?,
                                      header: [(String,String)]?) -> OtlpHttpLogExporter {
    return OtlpHttpLogExporter(endpoint: URL(string: endpoint)!, // NOTE: make sure about extra validations (format/non-empty)
                               config: OtlpConfiguration(
                                  timeout: convertToTimeInterval(from: timeout)!,  // NOTE: make sure about extra validations (not-nil)
                                  headers: header
                               )
    );
  }
  
  func setHttpExporters(_ spanConfigDict: NSDictionary?,
                        logConfigDict: NSDictionary?) -> OpenTelemetryExport {
    
    var customSpanExporter: OtlpHttpTraceExporter? = nil
    var customLogExporter: OtlpHttpLogExporter? = nil
        
    // OTLP HTTP Trace Exporter
    if spanConfigDict != nil {
      let spanConfig = CustomExporterConfig.fromDictionary(spanConfigDict!)
      customSpanExporter = self.setOtlpHttpTraceExporter(endpoint: spanConfig.endpoint,
                                                         timeout: spanConfig.timeout!,
                                                         header: spanConfig.header!);
    }
    
    // OTLP HTTP Log Exporter
    if logConfigDict != nil {
      let logConfig = CustomExporterConfig.fromDictionary(logConfigDict!)
      customLogExporter = self.setOtlpHttpLogExporter(endpoint: logConfig.endpoint,
                                                      timeout: logConfig.timeout!,
                                                      header: logConfig.header!)
    }
            
      return OpenTelemetryExport(spanExporter: customSpanExporter, logExporter: customLogExporter);
    }
    
    @objc(startNativeEmbraceSDK:otlpExportConfigDict:resolver:rejecter:)
    func startNativeEmbraceSDK(sdkConfigDict: NSDictionary, otlpExportConfigDict: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let config = SDKConfig(from: sdkConfigDict)

        DispatchQueue.main.async {
            do {
                var embraceOptions: Embrace.Options {
                    var crashReporter: CrashReporter?
                    if config.disableCrashReporter {
                        crashReporter = nil
                    } else {
                        crashReporter = EmbraceCrashReporter()
                    }

                    let servicesBuilder = CaptureServiceBuilder().addDefaults()
                    if config.disableAutomaticViewCapture {
                            servicesBuilder.remove(ofType: ViewCaptureService.self)
                    }

                    var endpoints: Embrace.Endpoints?
                    if config.endpointBaseUrl != nil {
                        endpoints = Embrace.Endpoints(baseURL: config.endpointBaseUrl!,
                                                      developmentBaseURL: config.endpointBaseUrl!,
                                                      configBaseURL: config.endpointBaseUrl!)
                    }
                    
                    var customExporters: OpenTelemetryExport = self.setHttpExporters(otlpExportConfigDict["traceExporter"] as? NSDictionary,
                                                                                     logConfigDict: otlpExportConfigDict["logExporter"] as? NSDictionary)

                    return .init(
                        appId: config.appId,
                        appGroupId: config.appGroupId,
                        platform: .reactNative,
                        endpoints: endpoints,
                        captureServices: servicesBuilder.build(),
                        crashReporter: crashReporter,
                        export: customExporters
                    )
                }

                try Embrace.setup(options: embraceOptions)
                    .start()

                resolve(true)
            } catch let error {
                reject("START_EMBRACE_SDK", "Error starting Embrace SDK", error)
            }
        }
    }
  // Http ends
  
//  // Grpc starts
//  private func setOtlpGrpcTraceExporter(endpoint: String,
//                                        timeout: NSNumber,
//                                        header: [(String,String)]?) -> OtlpTraceExporter {
//    return OtlpTraceExporter(endpoint: endpoint,
//                               config: OtlpConfiguration(
//                                  timeout: timeout as TimeInterval,
//                                  headers: header
//                               )
//    );
//  }
//
//  private func setOtlpGrpcLogExporter(endpoint: String,
//                                      header: [(String,String)]?,
//                                      timeout: NSNumber?) -> OtlpLogExporter {
//    return OtlpLogExporter(endpoint: endpoint,
//                             config: OtlpConfiguration(
//                                timeout: timeout as TimeInterval,
//                                headers: header
//                             )
//    );
//  }
//
//  @objc(setGrpcExporters:logConfig:resolver:rejecter:)
//  func setGrpExporters(_ spanConfigDict: NSDictionary?,
//                              logConfigDict: NSDictionary?,
//                              resolver resolve: @escaping RCTPromiseResolveBlock,
//                              rejecter reject: @escaping RCTPromiseRejectBlock) -> OpenTelemetryExport? {
//    if (spanConfigDict == nil && logConfigDict == nil) {
//      reject("SET_OTLP_GRPC_CUSTOM_EXPORTER", "Error setting grpc custom exporter, it should receive at least one configuration", nil)
//      return nil
//    }
//
//    let customSpanExporter: OtlpTraceExporter?
//    let customLogExporter: OtlpLogExporter?
//
//    // OTLP GRPC Trace Exporter
//    if spanConfigDict != nil {
//      var spanConfig = CustomExporterConfig.fromDictionary(spanConfigDict!)
//      customSpanExporter = this.setOtlpGrpcTraceExporter(endpoint: spanConfig.endpoint,
//                                                         header: spanConfig.header,
//                                                         timeout: spanConfig?.timeout);
//    }
//
//    // OTLP GRPC Log Exporter
//    if logConfigDict != nil {
//      var spanConfig = CustomExporterConfig.fromDictionary(logConfigDict!)
//      customLogExporter = this.setOtlpGrpcLogExporter(endpoint: logConfig.endpoint,
//                                                      header: logConfig.header,
//                                                      timeout: logConfig.timeout)
//    }
//
//    resolve(OpenTelemetryExport(spanExporter: customSpanExporter, logExporter: customLogExporter))
//  }
}
