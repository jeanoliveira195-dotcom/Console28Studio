//! Motor DSP de autoria própria para o Console 28 Studio.
//!
//! **NÃO é** um modelo validado ou licenciado do console Neve 8028.
//! Cadeia: filtro passa-altas -> EQ -> separação de graves -> compressão grave
//! com detector vinculado entre canais -> saturação discreta ADAA -> bloqueio DC ->
//! mistura -> ganho -> limitador de pico vinculado. Não é limitador true-peak.
//! Sem alocações de memória durante o processamento de um bloco.

use std::f64::consts::PI;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Settings {
    pub enabled: bool,
    pub drive: f64,
    pub warmth: f64,
    pub bass_compression: f64,
    pub presence: f64,
    pub air: f64,
    pub mix: f64,
    pub output_db: f64,
    pub low_cut_hz: f64,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            enabled: true, drive: 0.12, warmth: 0.65, bass_compression: 0.25,
            presence: 0.60, air: 0.46, mix: 0.82, output_db: -1.2, low_cut_hz: 65.0,
        }
    }
}

impl Settings {
    pub fn from_wire(enabled: bool, v: &[f32]) -> Option<Self> {
        if v.len() != 8 || v.iter().any(|n| !n.is_finite()) { return None; }
        Some(Self {
            enabled,
            drive: (v[0] as f64).clamp(0.0, 1.0),
            warmth: (v[1] as f64).clamp(0.0, 1.0),
            presence: (v[2] as f64).clamp(0.0, 1.0),
            air: (v[3] as f64).clamp(0.0, 1.0),
            mix: (v[4] as f64).clamp(0.0, 1.0),
            output_db: (v[5] as f64).clamp(-18.0, 6.0),
            low_cut_hz: (v[6] as f64).clamp(20.0, 160.0),
            bass_compression: (v[7] as f64).clamp(0.0, 1.0),
        })
    }
}

#[derive(Default, Clone, Copy)]
struct Biquad {
    b0: f64, b1: f64, b2: f64, a1: f64, a2: f64, z1: f64, z2: f64,
}

impl Biquad {
    fn reset(&mut self) { self.z1 = 0.0; self.z2 = 0.0; }
    fn set(&mut self, b0:f64, b1:f64, b2:f64, a0:f64, a1:f64, a2:f64) {
        self.b0=b0/a0; self.b1=b1/a0; self.b2=b2/a0;
        self.a1=a1/a0; self.a2=a2/a0;
    }
    #[inline(always)]
    fn tick(&mut self, x: f64) -> f64 {
        let y=self.b0*x + self.z1;
        self.z1=self.b1*x-self.a1*y+self.z2;
        self.z2=self.b2*x-self.a2*y;
        if y.is_finite() { y } else { self.reset(); 0.0 }
    }
    fn high_pass(&mut self, fs:f64, hz:f64) {
        let w=2.0*PI*hz.min(fs*0.42)/fs;
        let c=w.cos(); let alpha=w.sin()/(2.0*0.70710678118);
        self.set((1.0+c)/2.0,-(1.0+c),(1.0+c)/2.0,
                 1.0+alpha,-2.0*c,1.0-alpha);
    }
    fn low_pass(&mut self, fs:f64, hz:f64) {
        let w=2.0*PI*hz.min(fs*0.42)/fs;
        let c=w.cos(); let alpha=w.sin()/(2.0*0.70710678118);
        self.set((1.0-c)/2.0, 1.0-c, (1.0-c)/2.0,
                 1.0+alpha, -2.0*c, 1.0-alpha);
    }
    fn peak(&mut self, fs:f64, hz:f64, q:f64, db:f64) {
        let w=2.0*PI*hz.min(fs*0.42)/fs;
        let c=w.cos(); let alpha=w.sin()/(2.0*q); let a=10.0_f64.powf(db/40.0);
        self.set(1.0+alpha*a,-2.0*c,1.0-alpha*a,
                 1.0+alpha/a,-2.0*c,1.0-alpha/a);
    }
    fn shelf(&mut self, fs:f64, hz:f64, db:f64, high:bool) {
        let w=2.0*PI*hz.min(fs*0.42)/fs; let c=w.cos();
        let a=10.0_f64.powf(db/40.0);
        let alpha=w.sin()*2.0_f64.sqrt()/2.0; // Inclinação S=1: RBJ
        let root=2.0*a.sqrt()*alpha;
        if high {
            self.set(a*((a+1.0)+(a-1.0)*c+root),
                     -2.0*a*((a-1.0)+(a+1.0)*c),
                     a*((a+1.0)+(a-1.0)*c-root),
                     (a+1.0)-(a-1.0)*c+root,
                     2.0*((a-1.0)-(a+1.0)*c),
                     (a+1.0)-(a-1.0)*c-root);
        } else {
            self.set(a*((a+1.0)-(a-1.0)*c+root),
                     2.0*a*((a-1.0)-(a+1.0)*c),
                     a*((a+1.0)-(a-1.0)*c-root),
                     (a+1.0)+(a-1.0)*c+root,
                     -2.0*((a-1.0)+(a+1.0)*c),
                     (a+1.0)+(a-1.0)*c-root);
        }
    }
}

struct Channel {
    hp: Biquad, bass: Biquad, mid: Biquad, treble: Biquad, bass_band: Biquad,
    previous_for_adaa: f64, dc_x: f64, dc_y: f64,
}

impl Channel {
    fn new() -> Self {
        Self { hp:Biquad::default(),bass:Biquad::default(),
            mid:Biquad::default(),treble:Biquad::default(),bass_band:Biquad::default(),
            previous_for_adaa:0.0,dc_x:0.0,dc_y:0.0 }
    }
    fn reset(&mut self) {
        self.hp.reset();self.bass.reset();self.mid.reset();self.treble.reset();self.bass_band.reset();
        self.previous_for_adaa=0.0;self.dc_x=0.0;self.dc_y=0.0;
    }
}

/// Motor isolado por sessão; não deve ser compartilhado entre threads sem sincronização.
pub struct Engine {
    sample_rate: f64,
    channels: Vec<Channel>,
    settings: Settings,
    gain: f64,
    bass_envelope: f64,
    limiter_gain: f64,
    dirty: bool,
}

impl Engine {
    pub fn new(sample_rate: u32, channel_count:u32) -> Option<Self> {
        if !(8_000..=192_000).contains(&sample_rate) || !(1..=8).contains(&channel_count) {
            return None;
        }
        let mut engine=Self {
            sample_rate:sample_rate as f64,
            channels:(0..channel_count).map(|_| Channel::new()).collect(),
            settings:Settings::default(),gain:1.0,bass_envelope:0.0,limiter_gain:1.0,dirty:true,
        };
        engine.tune();
        Some(engine)
    }
    pub fn set_settings(&mut self, settings: Settings) {
        if self.settings != settings {
            if !self.settings.enabled && settings.enabled { self.reset(); }
            self.settings=settings;
            self.dirty=true;
        }
    }
    pub fn reset(&mut self) {
        for ch in &mut self.channels { ch.reset(); }
        self.bass_envelope=0.0;
        self.limiter_gain=1.0;
    }
    fn tune(&mut self) {
        if !self.dirty { return; }
        self.dirty=false;
        let s=self.settings;
        // Bandas de EQ musicais, sem pretender reproduzir curvas reais do 1084.
        let bass_db=(s.warmth-0.5)*12.0; // faixa: -6 a +6 dB, valor musical ~+3.5 dB
        let mid_db=(s.presence-0.5)*6.0;
        let treble_db=(s.air-0.5)*5.0;
        for ch in &mut self.channels {
            ch.hp.high_pass(self.sample_rate,s.low_cut_hz);
            ch.bass.shelf(self.sample_rate,145.0,bass_db,false);
            ch.bass_band.low_pass(self.sample_rate,155.0);
            ch.mid.peak(self.sample_rate,2_400.0,1.0,mid_db);
            ch.treble.shelf(self.sample_rate,8_500.0,treble_db,true);
        }
        self.gain=10.0_f64.powf(s.output_db/20.0);
    }
    #[inline(always)]
    fn log_cosh(x:f64) -> f64 {
        // Numericamente estável inclusive fora do intervalo esperado.
        let a=x.abs(); a+(-2.0*a).exp().ln_1p()-(2.0_f64).ln()
    }
    #[inline(always)]
    fn saturate_adaa(x:f64, ch:&mut Channel, drive:f64) -> f64 {
        if drive <= 0.001 {
            ch.previous_for_adaa=x;
            return x;
        }
        // Saturação assimétrica, com redução de alias por antiderivada (ADAA1).
        // NÃO equivale a um transformador físico medido.
        let k=1.0+3.0*drive;
        let bias=0.11+0.19*drive;
        let bias_tanh=bias.tanh();
        let f=|u:f64| -> f64 { (Self::log_cosh(k*u+bias)-Self::log_cosh(bias))/k-u*bias_tanh };
        let prev=ch.previous_for_adaa;
        ch.previous_for_adaa=x;
        let integrated = if (x-prev).abs()>1e-5 {
            (f(x)-f(prev))/(x-prev)
        } else {
            ((x+prev)*0.5*k+bias).tanh()-bias_tanh
        };
        integrated*(1.0+1.05*drive)/k
    }
    /// PCM intercalado 16-bit, in-place e sem alocações no caminho quente.
    /// Compressão exclusiva da banda grave com ligação estéreo/multicanal;
    /// o detector e o limitador não alteram independentemente a imagem estéreo.
    pub fn process(&mut self, input: &mut [i16]) -> Result<(), &'static str> {
        let count=self.channels.len();
        if input.len()%count!=0 { return Err("Bloco PCM não contém frames completos"); }
        let s=self.settings;
        if !s.enabled || s.mix<=0.0 { return Ok(()); } // bypass bit-exato
        self.tune();
        let fs=self.sample_rate;
        let dc_r=(-2.0*PI*8.0/fs).exp();
        let attack=(-1.0/(0.018*fs)).exp();
        let release=(-1.0/(0.155*fs)).exp();
        let limiter_release=(-1.0/(0.075*fs)).exp();
        let threshold=10.0_f64.powf(-22.0/20.0);
        let ratio=1.0+1.9*s.bass_compression;
        let makeup=10.0_f64.powf((1.75*s.bass_compression)/20.0);
        for frame in input.chunks_exact_mut(count) {
            let mut dry=[0.0_f64;8];
            let mut eq=[0.0_f64;8];
            let mut bass=[0.0_f64;8];
            let mut output=[0.0_f64;8];
            let mut detector=0.0_f64;
            for (index,(sample,ch)) in frame.iter().zip(&mut self.channels).enumerate() {
                dry[index]=(*sample as f64)/32768.0;
                let mut x=ch.hp.tick(dry[index]);
                x=ch.bass.tick(x);x=ch.mid.tick(x);x=ch.treble.tick(x);
                bass[index]=ch.bass_band.tick(x);
                detector=detector.max(bass[index].abs());
                eq[index]=x;
            }
            // Detector analógico idealizado: suavização de envoltória (18ms/155ms).
            let smoothing=if detector>self.bass_envelope {attack} else {release};
            self.bass_envelope=smoothing*self.bass_envelope+(1.0-smoothing)*detector;
            let reduction=if s.bass_compression>0.0 && self.bass_envelope>threshold {
                // Compressor descendente: só atenua picos da banda abaixo de 155 Hz.
                let over_db=20.0*(self.bass_envelope/threshold).log10();
                10.0_f64.powf((-over_db*(1.0-1.0/ratio))/20.0)
            } else {1.0};
            // Makeup moderado devolve peso/sustentação sem aumentar todos os transientes.
            let bass_gain=if s.bass_compression>0.0 {reduction*makeup} else {1.0};
            let mut max_out=0.0_f64;
            for (index,ch) in self.channels.iter_mut().enumerate() {
                let x=eq[index]+bass[index]*(bass_gain-1.0);
                let saturated=Self::saturate_adaa(x,ch,s.drive);
                // Atenua offset DC produzido pela assimetria da saturação.
                let wet=saturated-ch.dc_x+dc_r*ch.dc_y;
                ch.dc_x=saturated; ch.dc_y=wet;
                output[index]=(dry[index]*(1.0-s.mix)+wet*s.mix)*self.gain;
                max_out=max_out.max(output[index].abs());
            }
            // Limitador de amostra (não true peak), com ataque imediato para
            // evitar clipping e relaxamento suave: teto -0.45 dBFS aprox.
            let limit=0.95_f64;
            let target=if max_out>limit {limit/max_out} else {1.0};
            if target<self.limiter_gain {
                self.limiter_gain=target;
            } else {
                self.limiter_gain=limiter_release*self.limiter_gain
                    +(1.0-limiter_release)*target;
            }
            for (index,sample) in frame.iter_mut().enumerate() {
                let value=(output[index]*self.limiter_gain)
                    .clamp(-limit,limit);
                *sample=(value*32768.0).round() as i16;
            }
        }
        Ok(())
    }
}

// ABI C para a ponte JNI. Os ponteiros pertencem exclusivamente ao lado nativo e
// devem ser usados sequencialmente (jamais destruir durante processamento).
#[no_mangle]
pub extern "C" fn console28_create(sample_rate:u32, channels:u32)->*mut Engine {
    match Engine::new(sample_rate,channels) {
        Some(e)=>Box::into_raw(Box::new(e)), None=>std::ptr::null_mut(),
    }
}
#[no_mangle]
pub unsafe extern "C" fn console28_destroy(ptr:*mut Engine) {
    if !ptr.is_null() { drop(Box::from_raw(ptr)); }
}
#[no_mangle]
pub unsafe extern "C" fn console28_reset(ptr:*mut Engine) ->i32 {
    match ptr.as_mut(){Some(e)=>{e.reset();0},None=>-1}
}
#[no_mangle]
pub unsafe extern "C" fn console28_set_settings(ptr:*mut Engine, enabled:u8, args:*const f32, len:usize)->i32 {
    if args.is_null() || len!=8 {return -1;}
    let settings=Settings::from_wire(enabled!=0,std::slice::from_raw_parts(args,len));
    match (ptr.as_mut(),settings) {
        (Some(e),Some(s))=>{e.set_settings(s);0}, _=>-2,
    }
}
#[no_mangle]
pub unsafe extern "C" fn console28_process(ptr:*mut Engine, pcm:*mut i16, count:usize)->i32 {
    if pcm.is_null() {return -1;}
    match ptr.as_mut() {
        Some(e)=>match e.process(std::slice::from_raw_parts_mut(pcm,count)) {Ok(())=>0,Err(_)=>-2},
        None=>-3,
    }
}
