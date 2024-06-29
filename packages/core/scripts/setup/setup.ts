import EmbraceLogger from '../../src/logger';
import Wizard from '../util/wizard';
import {
  createEmbraceJSON,
  patchAppBuildGradle,
  patchBuildGradle,
  patchMainApplication,
} from './android';
import { apiToken, iosAppID, packageJSON } from './common';
import {
  addUploadBuildPhase,
  createEmbracePlist,
  iosInitializeEmbrace,
  iosPodfile,
  patchXcodeBundlePhase,
} from './ios';

const logger = new EmbraceLogger(console);

logger.log('initializing setup wizard');

const iosSetps = [
  iosInitializeEmbrace,
  iosPodfile,
  patchXcodeBundlePhase,
  addUploadBuildPhase,
  createEmbracePlist,
];

const androidSteps = [
  patchBuildGradle,
  patchAppBuildGradle,
  createEmbraceJSON,
  patchMainApplication,
];

const run = () => {
  const wiz = new Wizard();
  [iosAppID, apiToken, packageJSON].map((field) => wiz.registerField(field));
  [...iosSetps, ...androidSteps].map((step) => wiz.registerStep(step));
  wiz.runSteps();
};

export default run;
