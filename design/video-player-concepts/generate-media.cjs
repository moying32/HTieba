const fs=require('fs'),path=require('path');
const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH ? {executablePath:process.env.CHROME_PATH} : {}),args:['--autoplay-policy=no-user-gesture-required']});
 const page=await browser.newPage();await page.setContent('<canvas id="scene"></canvas>');await page.addScriptTag({path:path.join(__dirname,'scene.js')});
 const media=await page.evaluate(async()=>{
  const results={};
  for(const type of ['portrait','landscape']){
   const canvas=document.createElement('canvas');canvas.width=type==='portrait'?720:1280;canvas.height=type==='portrait'?1280:720;
   paintScene(canvas,3.2);const jpg=canvas.toDataURL('image/jpeg',.92).split(',')[1];
   const stream=canvas.captureStream(24),audio=new AudioContext(),gain=audio.createGain(),dest=audio.createMediaStreamDestination();gain.gain.value=.018;gain.connect(dest);
   [146.83,220].forEach(freq=>{const o=audio.createOscillator();o.frequency.value=freq;o.type='sine';o.connect(gain);o.start()});await audio.resume();dest.stream.getAudioTracks().forEach(track=>stream.addTrack(track));
   const recorder=new MediaRecorder(stream,{mimeType:'video/webm;codecs=vp9,opus',videoBitsPerSecond:1800000});const chunks=[];recorder.ondataavailable=e=>chunks.push(e.data);
   const done=new Promise(resolve=>recorder.onstop=resolve);recorder.start();const start=performance.now();
   await new Promise(resolve=>{function frame(now){const elapsed=(now-start)/1000;paintScene(canvas,elapsed);if(elapsed<12)requestAnimationFrame(frame);else resolve()}requestAnimationFrame(frame)});
   recorder.stop();await done;await audio.close();stream.getTracks().forEach(t=>t.stop());
   const data=await new Promise(resolve=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result.split(',')[1]);reader.readAsDataURL(new Blob(chunks,{type:'video/webm'}))});results[type]={jpg,data};
  }
  return results;
 });
 for(const [name,data]of Object.entries(media)){fs.writeFileSync(path.join(__dirname,'assets',name+'.webm'),Buffer.from(data.data,'base64'));fs.writeFileSync(path.join(__dirname,'assets',name+'.jpg'),Buffer.from(data.jpg,'base64'))}
 await browser.close();console.log('Generated original portrait and landscape videos.');
})().catch(e=>{console.error(e);process.exit(1)});
