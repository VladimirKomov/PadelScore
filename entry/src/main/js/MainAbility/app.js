import hilog from '@ohos.hilog';

const LOG_DOMAIN = 0x5044;
const LOG_TAG = 'PadelScore';

export default {
  onCreate() {
    hilog.info(LOG_DOMAIN, LOG_TAG, '%{public}s', 'PadelScore application created');
  },
  onDestroy() {
    hilog.info(LOG_DOMAIN, LOG_TAG, '%{public}s', 'PadelScore application destroyed');
  }
}
