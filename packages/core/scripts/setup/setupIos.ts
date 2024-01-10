import EmbraceLogger from '../../src/logger';
import Wizard from '../util/wizard';
import { apiToken, iosAppID, packageJSON } from './common';
import {
  addUploadBuildPhase,
  createEmbracePlist,
  iosImportEmbrace,
  iosInitializeEmbrace,
  iosPodfile,
  patchXcodeBundlePhase,
} from './ios';

const logger = new EmbraceLogger(console);

logger.log('initializing setup wizard for ios');

const iosSetps = [
  iosImportEmbrace,
  iosInitializeEmbrace,
  iosPodfile,
  patchXcodeBundlePhase,
  addUploadBuildPhase,
  createEmbracePlist,
];

const run = () => {
  const wiz = new Wizard();
  [iosAppID, apiToken, packageJSON].map((field) => wiz.registerField(field));
  [...iosSetps].map((step) => wiz.registerStep(step));
  wiz.runSteps();
};

export default run;
