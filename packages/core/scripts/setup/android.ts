import Wizard from "../util/wizard";
import {NoopFile} from "../util/file";
import {FileUpdatable} from "../util/file";
import {
  buildAppGradlePatchable,
  buildGradlePatchable,
  embraceJSON,
  embraceJSONContents,
} from "../util/android";
import EmbraceLogger from "../../src/utils/EmbraceLogger";

import patch from "./patches/patch";
import {androidAppID, apiToken, packageJSON} from "./common";

const path = require("path");
const fs = require("fs");

const logger = new EmbraceLogger(console);

const androidToolsBuildGradleRE =
  /(\s+)classpath(\(|\s)("|')com\.android\.tools\.build:gradle(?::\d+(?:\.\d+)*)?("|')\)/;

export const androidEmbraceSwazzler =
  /classpath(\(|\s)('|")io\.embrace:embrace-swazzler:.*('|")\)?/;

export const androidGenericVersion =
  "classpath \"io.embrace:embrace-swazzler:${findProject(':embrace-io_react-native').properties['emb_android_sdk']}\"";

export const patchBuildGradle = {
  name: "patch build.gradle",
  run: (wizard: Wizard): Promise<any> => {
    return buildGradlePatchable().then(file => {
      if (file.hasLine(androidToolsBuildGradleRE)) {
        if (file.hasLine(androidEmbraceSwazzler)) {
          file.deleteLine(androidEmbraceSwazzler);
        }
        logger.log("Patching build.gradle file");
        file.addAfter(androidToolsBuildGradleRE, androidGenericVersion);
        file.patch();
        return;
      }

      logger.warn("Can't find file with com.android.tools.build:gradle");
      return;
    });
  },
  docURL:
    "https://embrace.io/docs/react-native/integration/add-embrace-sdk/?platform=android#manually",
};

const androidPlugin = /apply plugin: ("|')com.android.application("|')/;
export const androidEmbraceSwazzlerPluginRE =
  /apply plugin: ('|")embrace-swazzler('|")/;
export const androidEmbraceSwazzlerPlugin = "apply plugin: 'embrace-swazzler'";

export const patchAppBuildGradle = {
  name: "patch app/build.gradle",
  run: (wizard: Wizard): Promise<any> => {
    return buildAppGradlePatchable().then(file => {
      if (file.hasLine(androidPlugin)) {
        if (file.hasLine(androidEmbraceSwazzlerPluginRE)) {
          logger.warn("already has Embrace Swazzler plugin");
        } else {
          logger.log("patching app/build.gradle file");
          file.addAfter(androidPlugin, "\n" + androidEmbraceSwazzlerPlugin);
        }

        file.patch();
        return;
      }
      logger.warn('Can\'t find line: apply plugin: "com.android.application"');
      return;
    });
  },
  docURL:
    "https://embrace.io/docs/react-native/integration/add-embrace-sdk/?platform=android#manually",
};

export const createEmbraceJSON = {
  name: "create Embrace JSON file",
  run: (wizard: Wizard): Promise<any> => {
    return new Promise<FileUpdatable>((resolve: any) => {
      const p = path.join(
        "android",
        "app",
        "src",
        "main",
        "embrace-config.json",
      );

      try {
        fs.closeSync(fs.openSync(p, "ax"));
        return resolve(embraceJSON());
      } catch (e) {
        if (e instanceof Error && e.message.includes("EEXIST")) {
          logger.log("already has embrace-config.json file");
          return resolve(NoopFile);
        } else {
          throw e;
        }
      }
    }).then((file: FileUpdatable) => {
      if (file === NoopFile) {
        return;
      }
      return wizard
        .fieldValueList([androidAppID, apiToken])
        .then(list => {
          const [id, token] = list;
          file.contents = embraceJSONContents({appID: id, apiToken: token});
          return file.patch();
        })
        .then(() => {
          logger.log("adding embrace-config.json file in android/app/src/main");
        });
    });
  },
  docURL:
    "https://embrace.io/docs/react-native/integration/add-embrace-sdk/?platform=android#manually",
};

const tryToPatchMainApplication = () => {
  // In the future there will be more apps with kotlin than java so I start looking up by kotlin
  const response = patch("kotlin");
  if (!response) {
    return patch("java");
  }
  return response;
};

export const patchMainApplication = {
  name: "patch MainApplication file",
  run: (wizard: Wizard): Promise<any> => {
    return wizard.fieldValue(packageJSON).then(() => {
      return tryToPatchMainApplication();
    });
  },
  docURL:
    "https://embrace.io/docs/react-native/integration/session-reporting/#import-embrace",
};
