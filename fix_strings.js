const fs = require('fs');
const path = require('path');

function walk(dir) {
  let results = [];
  const list = fs.readdirSync(dir);
  list.forEach(file => {
    file = path.join(dir, file);
    const stat = fs.statSync(file);
    if (stat && stat.isDirectory()) {
      results = results.concat(walk(file));
    } else {
      results.push(file);
    }
  });
  return results;
}

const files = walk('C:/Users/RN8/Desktop/StickyApp-main/app/src/main/res').filter(f => f.endsWith('strings.xml'));

files.forEach(f => {
  const content = fs.readFileSync(f, 'utf8');
  const newContent = content.replace(/<string name="ai_remaining">([^<]*?)%d([^<]*?)%d([^<]*?)<\/string>/g, '<string name="ai_remaining">$1%1\\$d$2%2\\$d$3</string>');
  if (content !== newContent) {
    fs.writeFileSync(f, newContent, 'utf8');
    console.log('Fixed', f);
  }
});
