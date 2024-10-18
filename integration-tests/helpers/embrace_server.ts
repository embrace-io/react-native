import {EmbraceData} from "../typings/embrace";
import axios from "axios";
import {RAW_EMB_URL, SERVICE_NAME} from "./env";

const EMB_URL = RAW_EMB_URL.replace("{servicename}", SERVICE_NAME);

const getSessionPayloads = async (
  sessionId: string | string[],
  delay = 4000,
): Promise<EmbraceData> => {
  if (delay) {
    await new Promise(r => setTimeout(r, delay));
  }
  const response = await axios.get(EMB_URL);

  console.log("SESSION ID: ", sessionId);

  const responseData = (await response.data) as EmbraceData;

  const getDataFromSessionId = (array = [], sessionId) =>
    array.filter(r => {
      if (typeof sessionId === "string") {
        if (r.Body.includes(sessionId)) {
          return true;
        }
      } else if (sessionId instanceof Array) {
        return sessionId.some(sId => r.Body.includes(sId));
      }
    });

  const dataFiltered = {
    Spans: [],
    Events: [],
    Logs: [],
    Blobs: [],
    Crashes: [],
    Errors: [],
    Sessions: [],
  };

  Object.entries(responseData).forEach(([key, value]) => {
    dataFiltered[key] = getDataFromSessionId(value, sessionId).map(item =>
      JSON.parse(item.Body),
    );
  });

  return dataFiltered;
};

export {getSessionPayloads};
