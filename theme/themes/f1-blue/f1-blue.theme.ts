// The f1-blue theme. `astryx experimental theme editor` wrote this file once and never changes it again.
// The checked settings live in f1-blue.settings.json and the generated f1-blue.settings.ts;
// change them with `astryx experimental theme editor set f1-blue ...`. Add your own changes below: they win over the settings.
import {defineTheme} from '@astryxdesign/core/theme';
import {f1BlueSettings} from './f1-blue.settings';

export const f1BlueTheme = defineTheme({
  extends: f1BlueSettings,
  name: 'f1-blue',
});
