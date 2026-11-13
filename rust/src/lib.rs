//! Motor DSP de autoria própria para o Console 28 Studio.
//!
//! **NÃO é** um modelo validado ou licenciado do console Neve 8028.
//! Cadeia: filtro passa-altas -> shelf graves -> bell presença -> shelf agudos ->
//! saturação suavemente assimétrica ADAA -> bloqueio de DC -> mistura -> ganho -> pico seguro.
//! Sem alocações de memória durante o processamento de um bloco.

use std::f64::consts::PI;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Settings {
    pub enabled: bool,
    pub drive: f64,
    pub warmth: f64,
    pub presence: f64,
    pub air: f64,
    pub mix: f64,
    pub output_db: f64,
    pub low_cut_hz: f64,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            enabled: true, drive: 0.18, warmth: 0.65, presence: 0.60,
            air: 0.46, mix: 0.82, output_db: -0.7, low_cut_hz: 65.0,
        }
    }
}

impl Settings {
    pub fn from_wire(enabled: bool, v: &[f32]) -> Option<Self> {
        if v.len() != 7 || v.iter().any(|n| !n.is_finite()) { return None; }
        Some(Self {
            enabled,
            drive: (v[0] as f64).clamp(0.0, 1.0),
            warmth: (v[1] as f64).clamp(0.0, 1.0),
            presence: (v[2] as f64).clamp(0.0, 1.0),
            air: (v[3] as f64).clamp(0.0, 1.0),
            mix: (v[4] as f64).clamp(0.0, 1.0),
            output_db: (v[5] as f64).clamp(-18.0, 6.0),
            low_cut_hz: (v[6] as f64).clamp(20.0, 160.0),
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
    hp: Biquad, bass: Biquad, mid: Biquad, treble: Biquad,
    previous_for_adaa: f64, dc_x: f64, dc_y: f64,
}

impl Channel {
    fn new() -> Self {
        Self { hp:Biquad::default(),bass:Biquad::default(),
            mid:Biquad::default(),treble:Biquad::default(),
            previous_for_adaa:0.0,dc_x:0.0,dc_y:0.0 }
    }
    fn reset(&mut self) {
        self.hp.reset();self.bass.reset();self.mid.reset();self.treble.reset();
        self.previous_for_adaa=0.0;self.dc_x=0.0;self.dc_y=0.0;
    }
}

/// Motor isolado por sessão; não deve ser compartilhado entre threads sem sincronização.
pub struct Engine {
    sample_rate: f64,
    channels: Vec<Channel>,
    settings: Settings,
    gain: f64,
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
            settings:Settings::default(),gain:1.0,dirty:true,
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
    }
    fn tune(&mut self) {
        if !self.dirty { return; }
        self.dirty=false;
        let s=self.settings;
        // Bandas de EQ musicais, sem pretender reproduzir curvas reais do 1084.
        let bass_db=(s.warmth-0.5)*8.0;
        let mid_db=(s.presence-0.5)*6.0;
        let treble_db=(s.air-0.5)*5.0;
        for ch in &mut self.channels {
            ch.hp.high_pass(self.sample_rate,s.low_cut_hz);
            ch.bass.shelf(self.sample_rate,135.0,bass_db,false);
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
    /// Entrada e saída PCM intercalado 16-bit, operação in-place e sem heap no caminho quente.
    pub fn process(&mut self, input: &mut [i16]) -> Result<(), &'static str> {
        let channels=self.channels.len();
        if input.len()%channels!=0 { return Err("Bloco PCM não contém frames completos"); }
        let s=self.settings;
        if !s.enabled || s.mix<=0.0 { return Ok(()); } // bypass bit-exato
        self.tune();
        let fs=self.sample_rate;
        let dc_r=(-2.0*PI*8.0/fs).exp();
        for frame in input.chunks_exact_mut(channels) {
            for (sample,ch) in frame.iter_mut().zip(&mut self.channels) {
                let dry=(*sample as f64)/32768.0;
                let mut x=ch.hp.tick(dry);
                x=ch.bass.tick(x); x=ch.mid.tick(x); x=ch.treble.tick(x);
                let saturated=Self::saturate_adaa(x,ch,s.drive);
                // Bloqueio de DC com corte baixo; evita desvio gerado pela assimetria.
                let wet=saturated-ch.dc_x+dc_r*ch.dc_y;
                ch.dc_x=saturated; ch.dc_y=wet;
                let y=(dry*(1.0-s.mix)+wet*s.mix)*self.gain;
                // Soft-ceiling de amostras; não é limitador true-peak.
                let ay=y.abs();
                let limited=if ay>0.95 { y.signum()*(0.95+0.035*((ay-0.95)/0.035).tanh()) } else { y };
                let pcm=(limited.clamp(-1.0,32767.0/32768.0)*32768.0).round();
                *sample=pcm as i16;
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
    if args.is_null() || len!=7 {return -1;}
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
