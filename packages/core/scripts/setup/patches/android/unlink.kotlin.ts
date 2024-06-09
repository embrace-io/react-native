import { mainApplicationPatchable } from '../../../util/android';
import { FileUpdatable } from '../../../util/file';

import { EMBRACE_IMPORT_KOTLIN, EMBRACE_INIT_KOTLIN } from './patch.kotlin';

const unlinkKotlin = (): Promise<FileUpdatable> => {
  return new Promise(async (resolve, reject) => {
    const mainApplication = await mainApplicationPatchable('kotlin').catch(
      reject
    );

    if (!mainApplication) {
      reject(undefined);
      return;
    }

    mainApplication.deleteLine(EMBRACE_IMPORT_KOTLIN);
    mainApplication.deleteLine(EMBRACE_INIT_KOTLIN);
    resolve(mainApplication);
  });
};

export { unlinkKotlin };
