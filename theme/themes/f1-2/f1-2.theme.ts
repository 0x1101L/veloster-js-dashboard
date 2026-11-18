// The f1-2 theme. `astryx experimental theme editor` wrote this file once and never changes it again.
// The checked settings live in f1-2.settings.json and the generated f1-2.settings.ts;
// change them with `astryx experimental theme editor set f1-2 ...`. Add your own changes below: they win over the settings.
import {defineTheme} from '@astryxdesign/core/theme';
import {f12Settings} from './f1-2.settings';

export const f12Theme = defineTheme({
  extends: f12Settings,
  name: 'f1-2',
});
