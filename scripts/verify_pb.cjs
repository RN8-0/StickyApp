const PB = 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
async function main() {
  const res = await fetch(PB+'/api/collections/_superusers/auth-with-password', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({identity:'arainunger@gmail.com',password:'StickyAdmin2026!'})});
  const {token} = await res.json();
  const h = {Authorization:token};
  const s = await (await fetch(PB+'/api/collections/stickers/records?perPage=1&fields=id',{headers:h})).json();
  const p = await (await fetch(PB+'/api/collections/premium_stickers/records?perPage=1&fields=id',{headers:h})).json();
  const badCat = await (await fetch(PB+'/api/collections/stickers/records?perPage=1&filter=category%3D%22%22&fields=id',{headers:h})).json();
  console.log('stickers total:', s.totalItems);
  console.log('premium_stickers total:', p.totalItems);
  console.log('stickers with empty category:', badCat.totalItems);
  
  // Sample 3 records
  const sample = await (await fetch(PB+'/api/collections/stickers/records?perPage=3&fields=name,category,stickers',{headers:h})).json();
  sample.items.forEach(r => {
    const url = (r.stickers || [])[0]?.url || '';
    console.log('name:', r.name.substring(0, 30), '| cat:', r.category, '| firebaseURL:', url.includes('firebasestorage.googleapis.com'));
  });
  const pSample = await (await fetch(PB+'/api/collections/premium_stickers/records?perPage=3&fields=name,category',{headers:h})).json();
  pSample.items.forEach(r => console.log('premium:', r.name.substring(0,30), '| cat:', r.category));
}
main().catch(console.error);
