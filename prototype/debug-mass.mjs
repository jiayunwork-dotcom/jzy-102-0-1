import { readFileSync } from 'fs';
// reuse erosion5 by extracting functions via quick inline copy through import of source text
let src = readFileSync('./erosion5.mjs','utf8');
src = src.slice(0, src.indexOf('const size = 80'));
src += '\nexport { generate, erode, mulberry32, makeBrush };';
const tmp = './_erosion5lib.mjs';
writeFileSync_(tmp, src);
import { writeFileSync } from 'fs';
function writeFileSync_(f,s){writeFileSync(f,s);}
